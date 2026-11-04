package io.github.jakobfa.webtechproject.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.RateLimiter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Der MCP-Server von quaelmich (Model Context Protocol, Streamable HTTP ohne Streaming): Eine KI-App des Nutzers
 * (z. B. Claude) verbindet sich als Konnektor und kann seinen Plan lesen. Jede Anfrage braucht ein Zugriffstoken aus
 * der OAuth-Anmeldung; die Daten gehören immer dem Nutzer hinter dem Token.
 */
@RestController
public class McpController {

	private static final Logger log = LoggerFactory.getLogger(McpController.class);

	static final String LATEST_PROTOCOL = "2025-06-18";
	static final Set<String> SUPPORTED_PROTOCOLS = Set.of("2025-06-18", "2025-03-26", "2024-11-05");
	/** Aufrufe je Zugang und Stunde: genug für ein langes Gespräch, zu wenig zum Abgrasen */
	static final RateLimiter.Limit CALL_LIMIT = new RateLimiter.Limit(300, java.time.Duration.ofHours(1));

	private final OAuthService oauth;
	private final McpTools tools;
	private final RateLimiter rateLimiter;
	private final ObjectMapper mapper;
	private final String apiUrl;

	public McpController(OAuthService oauth, McpTools tools, RateLimiter rateLimiter, ObjectMapper mapper,
			@Value("${app.public.api-url:https://api.quaelmich.com}") String apiUrl) {
		this.oauth = oauth;
		this.tools = tools;
		this.rateLimiter = rateLimiter;
		this.mapper = mapper;
		this.apiUrl = apiUrl;
	}

	/** Wir schicken nichts von uns aus (kein Streaming): GET ist nicht vorgesehen */
	@GetMapping(path = "/mcp")
	public ResponseEntity<Void> noStream() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
	}

	@PostMapping(path = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> post(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth,
			@RequestBody JsonNode body) {
		Optional<OAuthService.Principal> principal = oauth.authenticate(bearer(auth));
		if (principal.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE,
					"Bearer resource_metadata=\"" + apiUrl + "/.well-known/oauth-protected-resource\"").build();
		}
		if (!rateLimiter.tryAcquire("mcp:" + principal.get().tokenId(), CALL_LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		if (body.isArray()) {
			List<Object> responses = new ArrayList<>();
			for (JsonNode message : body) {
				handle(principal.get(), message).ifPresent(responses::add);
			}
			return responses.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(responses);
		}
		return handle(principal.get(), body).<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.accepted().build());
	}

	private static String bearer(String header) {
		return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
	}

	/** Eine JSON-RPC-Nachricht; leer, wenn keine Antwort nötig ist (Benachrichtigung oder Antwort des Clients) */
	private Optional<Map<String, Object>> handle(OAuthService.Principal principal, JsonNode message) {
		JsonNode idNode = message.get("id");
		JsonNode methodNode = message.get("method");
		if (methodNode == null || !methodNode.isString()) {
			return Optional.empty(); // eine Antwort des Clients oder Unsinn: nichts zu tun
		}
		if (idNode == null || idNode.isNull()) {
			return Optional.empty(); // Benachrichtigung (z. B. notifications/initialized)
		}
		Object id = idNode.isNumber() ? idNode.numberValue() : idNode.asString();
		String method = methodNode.asString();
		JsonNode params = message.get("params");
		try {
			return Optional.of(result(id, switch (method) {
				case "initialize" -> initialize(params);
				case "ping" -> Map.of();
				case "tools/list" -> Map.of("tools", visibleTools(principal));
				case "tools/call" -> callTool(principal, params);
				default -> throw new RpcException(-32601, "Methode nicht unterstützt: " + method);
			}));
		} catch (RpcException e) {
			return Optional.of(error(id, e.code, e.getMessage()));
		}
	}

	private Map<String, Object> initialize(JsonNode params) {
		String requested = params != null && params.has("protocolVersion") ? params.get("protocolVersion").asString()
				: LATEST_PROTOCOL;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("protocolVersion", SUPPORTED_PROTOCOLS.contains(requested) ? requested : LATEST_PROTOCOL);
		result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
		result.put("serverInfo", Map.of("name", "quaelmich", "version", "1.0"));
		result.put("instructions", "quaelmich ist ein Trainingsplan für Radfahrer. Die Werkzeuge zeigen den Plan, die "
				+ "Fahrten und die Diagnose des angemeldeten Nutzers. Fang mit get_overview an. Alle Zahlen sind Minuten "
				+ "und TSS (Trainingsbelastung). Die Daten gehören dem Nutzer; erfinde nichts, was nicht in den "
				+ "Ergebnissen steht.");
		return result;
	}

	private List<Map<String, Object>> visibleTools(OAuthService.Principal principal) {
		return tools.tools().stream().filter(tool -> principal.may(tool.scope())).map(tool -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", tool.name());
			row.put("description", tool.description());
			row.put("inputSchema", tool.inputSchema());
			return row;
		}).toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> callTool(OAuthService.Principal principal, JsonNode params) {
		if (params == null || !params.has("name")) {
			throw new RpcException(-32602, "name fehlt");
		}
		String name = params.get("name").asString();
		McpTools.Tool tool = tools.tools().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow(() -> new RpcException(-32602, "Unbekanntes Werkzeug: " + name));
		if (!principal.may(tool.scope())) {
			throw new RpcException(-32602, "Für dieses Werkzeug fehlt die Erlaubnis (" + tool.scope() + ")");
		}
		Map<String, Object> arguments = params.has("arguments") && params.get("arguments").isObject()
				? mapper.convertValue(params.get("arguments"), Map.class) : Map.of();
		log.info("MCP Nutzer {} ruft {} auf", principal.userId(), name);
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			Object data = tools.call(principal.userId(), name, arguments);
			result.put("content", List.of(Map.of("type", "text", "text", mapper.writeValueAsString(data))));
			result.put("isError", false);
		} catch (McpTools.ToolException e) {
			result.put("content", List.of(Map.of("type", "text", "text", e.getMessage())));
			result.put("isError", true);
		}
		return result;
	}

	private static Map<String, Object> result(Object id, Object result) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("jsonrpc", "2.0");
		response.put("id", id);
		response.put("result", result);
		return response;
	}

	private static Map<String, Object> error(Object id, int code, String message) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("jsonrpc", "2.0");
		response.put("id", id);
		response.put("error", Map.of("code", code, "message", message));
		return response;
	}

	private static final class RpcException extends RuntimeException {
		private final int code;

		RpcException(int code, String message) {
			super(message);
			this.code = code;
		}
	}
}

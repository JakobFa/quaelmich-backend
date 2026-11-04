package io.github.jakobfa.webtechproject.mcp;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.auth.RateLimiter;
import io.github.jakobfa.webtechproject.mcp.OAuthService.OAuthException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Anmeldung für KI-Apps (OAuth 2.1): Entdeckung (Metadaten), Registrierung, Erlaubnis durch den Nutzer, Token.
 * Die Seite, auf der der Nutzer erlaubt, liegt im Frontend (dort ist er angemeldet).
 */
@RestController
public class OAuthController {

	/** Eine Registrierung ist offen für KI-Apps, aber begrenzt */
	static final RateLimiter.Limit REGISTER_LIMIT = new RateLimiter.Limit(20, java.time.Duration.ofHours(1));
	static final RateLimiter.Limit TOKEN_LIMIT = new RateLimiter.Limit(120, java.time.Duration.ofHours(1));

	public record RegisterRequest(String client_name, List<String> redirect_uris) {
	}

	public record ConsentRequest(String clientId, String redirectUri, String codeChallenge,
			String codeChallengeMethod, String scope, String state) {
	}

	private final OAuthService oauth;
	private final RateLimiter rateLimiter;
	private final String apiUrl;
	private final String frontendUrl;

	public OAuthController(OAuthService oauth, RateLimiter rateLimiter,
			@Value("${app.public.api-url:https://api.quaelmich.com}") String apiUrl,
			@Value("${app.public.frontend-url:https://quaelmich.com}") String frontendUrl) {
		this.oauth = oauth;
		this.rateLimiter = rateLimiter;
		this.apiUrl = apiUrl;
		this.frontendUrl = frontendUrl;
	}

	// ---------- Entdeckung ----------

	/** Wo ist der geschützte Dienst und wer meldet an (RFC 9728) */
	@GetMapping(path = { "/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp" })
	public Map<String, Object> protectedResource() {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("resource", apiUrl + "/mcp");
		meta.put("authorization_servers", List.of(apiUrl));
		meta.put("scopes_supported", OAuthService.SCOPES);
		meta.put("bearer_methods_supported", List.of("header"));
		return meta;
	}

	/** Die Adressen der Anmeldung (RFC 8414) */
	@GetMapping(path = { "/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/mcp" })
	public Map<String, Object> authorizationServer() {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("issuer", apiUrl);
		meta.put("authorization_endpoint", frontendUrl + "/oauth/authorize");
		meta.put("token_endpoint", apiUrl + "/oauth/token");
		meta.put("registration_endpoint", apiUrl + "/oauth/register");
		meta.put("response_types_supported", List.of("code"));
		meta.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
		meta.put("code_challenge_methods_supported", List.of("S256"));
		meta.put("token_endpoint_auth_methods_supported", List.of("none"));
		meta.put("scopes_supported", OAuthService.SCOPES);
		return meta;
	}

	// ---------- Registrierung der KI-App ----------

	@PostMapping(path = "/oauth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request,
			HttpServletRequest http) {
		if (!rateLimiter.tryAcquire("oauth-register:" + RateLimiter.clientIp(http), REGISTER_LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		OAuthClient client = oauth.register(request.client_name(), request.redirect_uris());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("client_id", client.getClientId());
		body.put("client_name", client.getName());
		body.put("redirect_uris", client.getRedirectUris());
		body.put("grant_types", List.of("authorization_code", "refresh_token"));
		body.put("response_types", List.of("code"));
		body.put("token_endpoint_auth_method", "none");
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	/** Für die Erlaubnis-Seite: Name der KI-App und wohin sie zurückleitet (öffentlich, nichts Geheimes) */
	@GetMapping(path = "/oauth/client")
	public ResponseEntity<Map<String, Object>> client(@RequestParam("client_id") String clientId,
			@RequestParam(name = "redirect_uri", required = false) String redirectUri) {
		return oauth.findClient(clientId).map(client -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("name", client.getName());
			body.put("redirectUriRegistered", redirectUri != null && client.getRedirectUris().contains(redirectUri));
			body.put("redirectHost", hostOf(redirectUri));
			return ResponseEntity.ok(body);
		}).orElse(ResponseEntity.notFound().build());
	}

	private static String hostOf(String uri) {
		try {
			return uri == null ? null : URI.create(uri).getHost();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---------- Erlaubnis durch den Nutzer (aus dem Frontend, mit Anmeldung) ----------

	@PostMapping(path = "/oauth/consent")
	public Map<String, Object> consent(@AuthenticationPrincipal Jwt jwt, @RequestBody ConsentRequest request) {
		String code = oauth.authorize(CurrentUser.id(jwt), request.clientId(), request.redirectUri(),
				request.codeChallenge(), request.codeChallengeMethod(), request.scope());
		StringBuilder url = new StringBuilder(request.redirectUri());
		url.append(request.redirectUri().contains("?") ? '&' : '?').append("code=")
				.append(URLEncoder.encode(code, StandardCharsets.UTF_8));
		if (request.state() != null && !request.state().isBlank()) {
			url.append("&state=").append(URLEncoder.encode(request.state(), StandardCharsets.UTF_8));
		}
		return Map.of("redirectUrl", url.toString());
	}

	// ---------- Token ----------

	@PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<Map<String, Object>> token(@RequestParam("grant_type") String grantType,
			@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "redirect_uri", required = false) String redirectUri,
			@RequestParam(name = "client_id", required = false) String clientId,
			@RequestParam(name = "code_verifier", required = false) String codeVerifier,
			@RequestParam(name = "refresh_token", required = false) String refreshToken, HttpServletRequest http) {
		if (!rateLimiter.tryAcquire("oauth-token:" + RateLimiter.clientIp(http), TOKEN_LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		OAuthService.Tokens tokens = switch (grantType) {
			case "authorization_code" -> oauth.exchangeCode(code, clientId, redirectUri, codeVerifier);
			case "refresh_token" -> oauth.refresh(refreshToken, clientId);
			default -> throw new OAuthException("unsupported_grant_type", "grant_type nicht unterstützt");
		};
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access_token", tokens.accessToken());
		body.put("token_type", "Bearer");
		body.put("expires_in", tokens.expiresInSeconds());
		body.put("refresh_token", tokens.refreshToken());
		body.put("scope", tokens.scope());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

	// ---------- Verwaltung durch den Nutzer ----------

	/** Die KI-Apps, die auf das Konto zugreifen dürfen */
	@GetMapping(path = "/me/ai-connections")
	public List<OAuthService.Connection> connections(@AuthenticationPrincipal Jwt jwt) {
		return oauth.connectionsOf(CurrentUser.id(jwt));
	}

	/** Zugang einer KI-App beenden */
	@DeleteMapping(path = "/me/ai-connections/{id}")
	public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return oauth.revoke(CurrentUser.id(jwt), id) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	@ExceptionHandler(OAuthException.class)
	public ResponseEntity<Map<String, Object>> oauthError(OAuthException e) {
		HttpStatus status = "invalid_client".equals(e.error()) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
				.body(Map.of("error", e.error(), "error_description", String.valueOf(e.getMessage())));
	}
}

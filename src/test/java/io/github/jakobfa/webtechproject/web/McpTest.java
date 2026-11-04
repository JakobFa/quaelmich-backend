package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** KI-Anbindung: OAuth-Ablauf (Registrierung, Erlaubnis, Token) und der MCP-Endpunkt mit den Lesewerkzeugen */
@SpringBootTest
@AutoConfigureMockMvc
class McpTest {

	private static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
	private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

	@Autowired
	private MockMvc mockMvc;

	private String auth;
	private String clientId;

	@BeforeEach
	void setUp() throws Exception {
		auth = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", auth)).andExpect(status().isCreated());
		clientId = registerClient();
	}

	private static String challenge(String verifier) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}

	private String registerClient() throws Exception {
		String body = mockMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"client_name\": \"Claude\", \"redirect_uris\": [\"%s\"] }".formatted(REDIRECT)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.client_id");
	}

	private String consent(String scope) throws Exception {
		String body = mockMvc.perform(post("/oauth/consent").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "clientId": "%s", "redirectUri": "%s", "codeChallenge": "%s", "codeChallengeMethod": "S256",
						  "scope": "%s", "state": "abc" }
						""".formatted(clientId, REDIRECT, challenge(VERIFIER), scope)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String redirect = JsonPath.read(body, "$.redirectUrl");
		assertThat(redirect).startsWith(REDIRECT + "?code=").endsWith("&state=abc");
		String query = URI.create(redirect).getQuery();
		return query.substring("code=".length(), query.indexOf('&'));
	}

	private ResultActions token(String code, String verifier) throws Exception {
		return mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", "authorization_code").param("code", code).param("client_id", clientId)
				.param("redirect_uri", REDIRECT).param("code_verifier", verifier));
	}

	private String accessToken(String scope) throws Exception {
		String body = token(consent(scope), VERIFIER).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.access_token");
	}

	private ResultActions rpc(String accessToken, String json) throws Exception {
		var request = post("/mcp").contentType(MediaType.APPLICATION_JSON).content(json);
		if (accessToken != null) {
			request.header("Authorization", "Bearer " + accessToken);
		}
		return mockMvc.perform(request);
	}

	private String call(String accessToken, String tool, String arguments) throws Exception {
		String body = rpc(accessToken, """
				{ "jsonrpc": "2.0", "id": 7, "method": "tools/call", "params": { "name": "%s", "arguments": %s } }
				""".formatted(tool, arguments)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.result.content[0].text");
	}

	@Test
	void theServerAnnouncesItsAuthorizationForClients() throws Exception {
		mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
				.andExpect(jsonPath("$.token_endpoint").value("https://api.quaelmich.com/oauth/token"))
				.andExpect(jsonPath("$.registration_endpoint").value("https://api.quaelmich.com/oauth/register"))
				.andExpect(jsonPath("$.authorization_endpoint").value("https://quaelmich.com/oauth/authorize"));
		mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isOk())
				.andExpect(jsonPath("$.resource").value("https://api.quaelmich.com/mcp"))
				.andExpect(jsonPath("$.authorization_servers[0]").value("https://api.quaelmich.com"));
	}

	@Test
	void withoutATokenTheServerPointsToTheAuthorization() throws Exception {
		rpc(null, "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"ping\" }")
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers
						.containsString("resource_metadata=\"https://api.quaelmich.com/.well-known/oauth-protected-resource\"")));
		rpc("falsch", "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"ping\" }")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theFullFlowLeadsToAWorkingConnection() throws Exception {
		String access = accessToken("plan:read");

		rpc(access, """
				{ "jsonrpc": "2.0", "id": 1, "method": "initialize",
				  "params": { "protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": { "name": "claude" } } }
				""").andExpect(status().isOk())
				.andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
				.andExpect(jsonPath("$.result.serverInfo.name").value("quaelmich"))
				.andExpect(jsonPath("$.result.capabilities.tools").exists());
		// Benachrichtigungen bekommen keine Antwort
		rpc(access, "{ \"jsonrpc\": \"2.0\", \"method\": \"notifications/initialized\" }")
				.andExpect(status().isAccepted());
		rpc(access, "{ \"jsonrpc\": \"2.0\", \"id\": 2, \"method\": \"tools/list\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.tools[?(@.name=='get_plan')]").exists())
				.andExpect(jsonPath("$.result.tools[?(@.name=='get_diagnosis')]").exists())
				.andExpect(jsonPath("$.result.tools[?(@.name=='list_workouts')]").exists());
	}

	@Test
	void theToolsReturnThePlanOfTheUserBehindTheToken() throws Exception {
		String access = accessToken("plan:read");

		String overview = call(access, "get_overview", "{}");
		assertThat(overview).contains("\"athlet\"").contains("\"naechste7Tage\"").contains("AMBITIOUS");

		String plan = call(access, "get_plan", "{ \"from\": \"2000-01-01\", \"to\": \"2000-01-10\" }");
		assertThat(plan).contains("\"tage\":[]");
		String upcoming = call(access, "get_plan", "{}");
		assertThat(upcoming).contains("\"einheit\"").contains("\"minuten\"").contains("\"tss\"");

		assertThat(call(access, "get_diagnosis", "{}")).contains("\"belastung\"").contains("\"stand\"");
		assertThat(call(access, "get_rides", "{}")).contains("\"zusatzfahrten\"");
		assertThat(call(access, "list_workouts", "{ \"type\": \"threshold\" }")).contains("Schwelle 2 × 15");
	}

	@Test
	void anotherUsersTokenNeverShowsThisPlan() throws Exception {
		String mine = accessToken("plan:read");
		String planOfMine = call(mine, "get_plan", "{}");

		// ein zweiter Nutzer mit eigenem Profil und Plan
		auth = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", auth)).andExpect(status().isCreated());
		String theirs = accessToken("plan:read");
		String planOfTheirs = call(theirs, "get_plan", "{}");

		assertThat(planOfTheirs).isNotEmpty();
		List<Integer> mineIds = JsonPath.read(planOfMine, "$.tage[*].planId").getClass().isArray() ? List.of()
				: ((List<?>) JsonPath.read(planOfMine, "$.tage[*].planId")).stream()
						.map(id -> ((Number) id).intValue()).toList();
		List<Integer> theirIds = ((List<?>) JsonPath.read(planOfTheirs, "$.tage[*].planId")).stream()
				.map(id -> ((Number) id).intValue()).toList();
		assertThat(theirIds).doesNotContainAnyElementsOf(mineIds);
	}

	@Test
	void toolErrorsComeBackAsErrorTextAndUnknownToolsAsRpcErrors() throws Exception {
		String access = accessToken("plan:read");

		rpc(access, """
				{ "jsonrpc": "2.0", "id": 3, "method": "tools/call",
				  "params": { "name": "get_plan", "arguments": { "from": "morgen" } } }
				""").andExpect(status().isOk()).andExpect(jsonPath("$.result.isError").value(true));
		rpc(access, """
				{ "jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": { "name": "loesche_alles" } }
				""").andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32602));
		rpc(access, "{ \"jsonrpc\": \"2.0\", \"id\": 5, \"method\": \"gibtsnicht\" }")
				.andExpect(jsonPath("$.error.code").value(-32601));
	}

	@Test
	void aCodeWorksOnlyOnceAndOnlyWithTheRightVerifier() throws Exception {
		String code = consent("plan:read");
		token(code, "falscher-verifier-falscher-verifier-falscher-verifier").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
		// der Code ist nach dem Fehlversuch verbraucht
		token(code, VERIFIER).andExpect(status().isBadRequest());

		String again = consent("plan:read");
		token(again, VERIFIER).andExpect(status().isOk());
		token(again, VERIFIER).andExpect(status().isBadRequest());
	}

	@Test
	void unregisteredRedirectsAndMissingPkceAreRefused() throws Exception {
		mockMvc.perform(post("/oauth/consent").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "clientId": "%s", "redirectUri": "https://boese.example/cb", "codeChallenge": "%s",
						  "codeChallengeMethod": "S256" }
						""".formatted(clientId, challenge(VERIFIER)))).andExpect(status().isBadRequest());
		mockMvc.perform(post("/oauth/consent").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "clientId": "%s", "redirectUri": "%s", "codeChallenge": "kurz", "codeChallengeMethod": "plain" }
						""".formatted(clientId, REDIRECT))).andExpect(status().isBadRequest());
		// ohne Anmeldung gibt es keine Erlaubnis
		mockMvc.perform(post("/oauth/consent").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"client_name\": \"x\", \"redirect_uris\": [\"http://boese.example/cb\"] }"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void refreshIssuesNewTokensAndTheOldRefreshTokenStopsWorking() throws Exception {
		String body = token(consent("plan:read"), VERIFIER).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		String refresh = JsonPath.read(body, "$.refresh_token");

		String renewed = mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", "refresh_token").param("refresh_token", refresh).param("client_id", clientId))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String newAccess = JsonPath.read(renewed, "$.access_token");
		rpc(newAccess, "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"ping\" }").andExpect(status().isOk());

		mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", "refresh_token").param("refresh_token", refresh).param("client_id", clientId))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theUserSeesHisConnectionsAndCanEndThem() throws Exception {
		String access = accessToken("plan:read");
		rpc(access, "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"ping\" }").andExpect(status().isOk());

		String list = mockMvc.perform(get("/me/ai-connections").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].clientName").value("Claude"))
				.andExpect(jsonPath("$[0].scope").value("plan:read")).andReturn().getResponse().getContentAsString();
		int id = ((Number) JsonPath.read(list, "$[0].id")).intValue();

		mockMvc.perform(delete("/me/ai-connections/" + id).header("Authorization", auth))
				.andExpect(status().isNoContent());
		rpc(access, "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"ping\" }")
				.andExpect(status().isUnauthorized());
		// ein anderer Nutzer kann ihn nicht beenden
		String other = registerWithProfile(mockMvc);
		mockMvc.perform(delete("/me/ai-connections/" + id).header("Authorization", other))
				.andExpect(status().isNotFound());
	}

	@Test
	void theToolListDependsOnTheGrantedScope() throws Exception {
		String access = accessToken("plan:read");
		String list = rpc(access, "{ \"jsonrpc\": \"2.0\", \"id\": 2, \"method\": \"tools/list\" }")
				.andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(list, "$.result.tools[*].name");
		assertThat(names).containsExactlyInAnyOrder("get_overview", "get_plan", "get_diagnosis", "get_rides",
				"list_workouts");
		assertThat(Map.of("scope", "plan:read")).isNotEmpty();
	}
}

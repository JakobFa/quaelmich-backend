package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Ohne app.mcp.enabled gibt es die KI-Anbindung nicht: alle Wege antworten mit 404 */
@SpringBootTest
@AutoConfigureMockMvc
class McpDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void mcpAndOAuthAreGone() throws Exception {
		mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
				.content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}")).andExpect(status().isNotFound());
		mockMvc.perform(get("/mcp")).andExpect(status().isNotFound());
		mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isNotFound());
		mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isNotFound());
		mockMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"client_name\":\"x\",\"redirect_uris\":[\"https://claude.ai/cb\"]}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/me/ai-connections")).andExpect(status().isNotFound());
	}

	@Test
	void everythingElseStillWorks() throws Exception {
		mockMvc.perform(get("/health")).andExpect(status().isOk());
	}
}

package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * In Tests ist kein API-Schlüssel gesetzt – die Anbindung muss dann gesperrt sein.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntervalsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void isNotAvailableWithoutApiKey() throws Exception {
		String token = registerWithProfile(mockMvc);

		mockMvc.perform(get("/me/intervals").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false));
	}

	@Test
	void refusesSyncWithoutApiKey() throws Exception {
		String token = registerWithProfile(mockMvc);

		mockMvc.perform(post("/me/intervals/sync").header("Authorization", token))
				.andExpect(status().isForbidden());
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(post("/me/intervals/sync")).andExpect(status().isUnauthorized());
	}

}

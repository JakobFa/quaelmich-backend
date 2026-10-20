package io.github.jakobfa.webtechproject.web;

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
		mockMvc.perform(get("/athletes/1/intervals"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false));
	}

	@Test
	void refusesSyncWithoutApiKey() throws Exception {
		mockMvc.perform(post("/athletes/1/intervals/sync")).andExpect(status().isForbidden());
	}

}

package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Mit eingeschalteter Bremse: zu viele Versuche von einer Adresse ergeben 429 */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void tooManyLoginAttemptsAreSlowedDown() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post("/auth/login").header("X-Forwarded-For", "198.51.100.1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{ \"email\": \"niemand@example.com\", \"password\": \"falsch\" }"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/auth/login").header("X-Forwarded-For", "198.51.100.1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"niemand@example.com\", \"password\": \"falsch\" }"))
				.andExpect(status().isTooManyRequests());
		// eine andere Adresse ist nicht betroffen
		mockMvc.perform(post("/auth/login").header("X-Forwarded-For", "198.51.100.2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"niemand@example.com\", \"password\": \"falsch\" }"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void resetMailsToOneAddressAreLimited() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/auth/password-reset").header("X-Forwarded-For", "198.51.100." + (10 + i))
					.contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"opfer@example.com\" }"))
					.andExpect(status().isAccepted());
		}
		mockMvc.perform(post("/auth/password-reset").header("X-Forwarded-For", "198.51.100.20")
				.contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"opfer@example.com\" }"))
				.andExpect(status().isTooManyRequests());
	}

}

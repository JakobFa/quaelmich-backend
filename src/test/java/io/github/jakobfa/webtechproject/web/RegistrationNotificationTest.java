package io.github.jakobfa.webtechproject.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.mail.MailService;

/** Mail an den Betreiber bei jeder neuen Registrierung */
@SpringBootTest(properties = "app.mail.admin-notify=betreiber@example.com")
@AutoConfigureMockMvc
class RegistrationNotificationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MailService mailService;

	private void register(String email) throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"geheim1234\" }".formatted(email)));
	}

	@Test
	void operatorGetsAMailAboutTheNewAccount() throws Exception {
		String email = "neu-" + UUID.randomUUID() + "@example.com";

		register(email);

		verify(mailService).send(eq("betreiber@example.com"), contains(email), contains("ohne Einladungscode"),
				anyString());
	}

	@Test
	void noMailWhenTheRegistrationFails() throws Exception {
		String email = "doppelt-" + UUID.randomUUID() + "@example.com";
		register(email);
		org.mockito.Mockito.clearInvocations(mailService);

		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"geheim1234\" }".formatted(email)))
				.andExpect(status().isConflict());

		verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
	}

}

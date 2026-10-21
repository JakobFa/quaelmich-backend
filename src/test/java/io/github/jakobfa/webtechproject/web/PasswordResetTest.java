package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.mail.MailService;

/**
 * "Passwort vergessen" von der Mail bis zum neuen Passwort. Der Mail-Versand ist durch eine Attrappe ersetzt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MailService mailService;

	private String email() {
		return "reset-" + UUID.randomUUID() + "@example.com";
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)));
	}

	private ResultActions requestReset(String email) throws Exception {
		return mockMvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\" }".formatted(email)));
	}

	private ResultActions confirm(String token, String password) throws Exception {
		return mockMvc.perform(post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"token\": \"%s\", \"password\": \"%s\" }".formatted(token, password)));
	}

	/** Holt den Token aus dem Link der zuletzt "verschickten" Mail */
	private String tokenFromMail(String email) {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailService, org.mockito.Mockito.atLeastOnce()).send(eq(email), anyString(), text.capture(),
				anyString());
		String body = text.getValue();
		return body.substring(body.indexOf("token=") + 6).split("\\s")[0];
	}

	@Test
	void resetsThePasswordWithTheLinkFromTheMail() throws Exception {
		when(mailService.frontendUrl(anyString())).thenAnswer(call -> "https://quaelmich.com" + call.getArgument(0));
		String email = email();
		register(email, "altes-passwort").andExpect(status().isCreated());

		requestReset(email.toUpperCase()).andExpect(status().isAccepted());
		String token = tokenFromMail(email);

		confirm(token, "neues-passwort").andExpect(status().isNoContent());
		login(email, "altes-passwort").andExpect(status().isUnauthorized());
		login(email, "neues-passwort").andExpect(status().isOk());
	}

	@Test
	void theLinkWorksOnlyOnce() throws Exception {
		when(mailService.frontendUrl(anyString())).thenAnswer(call -> "https://quaelmich.com" + call.getArgument(0));
		String email = email();
		register(email, "altes-passwort");
		requestReset(email);
		String token = tokenFromMail(email);

		confirm(token, "neues-passwort").andExpect(status().isNoContent());
		confirm(token, "noch-ein-passwort").andExpect(status().isBadRequest());
	}

	@Test
	void oldLoginsStopWorkingAfterTheReset() throws Exception {
		when(mailService.frontendUrl(anyString())).thenAnswer(call -> "https://quaelmich.com" + call.getArgument(0));
		String email = email();
		String response = register(email, "altes-passwort").andReturn().getResponse().getContentAsString();
		String oldLogin = "Bearer " + JsonPath.read(response, "$.token");
		mockMvc.perform(get("/auth/me").header("Authorization", oldLogin)).andExpect(status().isOk());

		Thread.sleep(1100); // JWTs sind sekundengenau – das alte Token muss echt älter sein
		requestReset(email);
		confirm(tokenFromMail(email), "neues-passwort");

		mockMvc.perform(get("/auth/me").header("Authorization", oldLogin)).andExpect(status().isUnauthorized());
		String fresh = "Bearer " + JsonPath.read(login(email, "neues-passwort").andReturn().getResponse()
				.getContentAsString(), "$.token");
		mockMvc.perform(get("/auth/me").header("Authorization", fresh)).andExpect(status().isOk());
	}

	@Test
	void unknownAddressGetsTheSameAnswerButNoMail() throws Exception {
		requestReset(email()).andExpect(status().isAccepted());

		verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void invalidTokenAndShortPasswordAreRejected() throws Exception {
		confirm("gibt-es-nicht", "neues-passwort").andExpect(status().isBadRequest());
		confirm("egal", "kurz").andExpect(status().isBadRequest());
	}

	@Test
	void requestingANewLinkInvalidatesTheOldOne() throws Exception {
		when(mailService.frontendUrl(anyString())).thenAnswer(call -> "https://quaelmich.com" + call.getArgument(0));
		String email = email();
		register(email, "altes-passwort");
		requestReset(email);
		String first = tokenFromMail(email);
		requestReset(email);
		String second = tokenFromMail(email);

		assertThat(second).isNotEqualTo(first);
		confirm(first, "neues-passwort").andExpect(status().isBadRequest());
		confirm(second, "neues-passwort").andExpect(status().isNoContent());
	}

}

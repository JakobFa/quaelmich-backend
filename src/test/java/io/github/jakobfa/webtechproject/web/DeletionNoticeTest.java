package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.integration.strava.StravaClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaAthlete;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.TokenResponse;
import io.github.jakobfa.webtechproject.mail.MailService;

/** Strava verlangt eine schriftliche Bestätigung, wenn Daten gelöscht wurden (API Policy §2.1, §2.5) */
@SpringBootTest
@AutoConfigureMockMvc
class DeletionNoticeTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private StravaClient client;
	@MockitoBean
	private MailService mailService;

	private String token;

	@BeforeEach
	void connected() throws Exception {
		token = registerNewUser(mockMvc);
		when(client.exchangeCode("abc")).thenReturn(new TokenResponse("acc", "ref", 4_000_000_000L,
				new StravaAthlete(System.nanoTime(), "Jakob")));
		mockMvc.perform(post("/me/strava").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"read,activity:read_all\" }")).andExpect(status().isOk());
	}

	@Test
	void disconnectingStravaSendsOneConfirmation() throws Exception {
		mockMvc.perform(delete("/me/strava").header("Authorization", token)).andExpect(status().isNoContent());

		verify(mailService, times(1)).send(startsWith("test-"), contains("Strava-Daten"), contains("gelöscht"),
				anyString());
	}

	@Test
	void deletingTheAccountSendsExactlyOneMailNotTwo() throws Exception {
		mockMvc.perform(post("/me/account/delete").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"geheim1234\" }"))
				.andExpect(status().isNoContent());

		verify(mailService, times(1)).send(startsWith("test-"), contains("Konto ist gelöscht"), anyString(),
				anyString());
		verify(mailService, never()).send(anyString(), contains("Strava-Daten"), anyString(), anyString());
	}
}

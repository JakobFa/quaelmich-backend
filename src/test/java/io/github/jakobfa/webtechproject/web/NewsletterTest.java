package io.github.jakobfa.webtechproject.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/** Rundmail: ohne Token unsichtbar, ohne send=true nur Vorschau, verschickt wird nur an die gezeigte Liste */
@SpringBootTest(properties = "app.mail.admin-notify=betreiber@example.com")
@AutoConfigureMockMvc
class NewsletterTest {

	private static final String TOKEN = "test-admin-token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository users;

	@MockitoBean
	private MailService mail;

	private String lina;
	private String ben;

	@BeforeEach
	void setUp() {
		NewsletterController.pauseMillis = 0;
		lina = "lina-" + UUID.randomUUID() + "@example.com";
		ben = "ben-" + UUID.randomUUID() + "@example.com";
		users.save(new AppUser(lina, "hash", Instant.now()));
		users.save(new AppUser(ben, "hash", Instant.now()));
		when(mail.send(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(true);
	}

	/** Andere Tests legen auch Nutzer an – darum immer nur an die beiden von hier */
	private String both() {
		return ", \"only\": [\"" + lina + "\", \"" + ben + "\"]";
	}

	private ResultActions call(String token, String extra) throws Exception {
		var request = post("/internal/newsletter").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subject\": \"Neu\", \"text\": \"Hallo\", \"html\": \"<p>Hallo</p>\", "
						+ "\"replyTo\": \"team@example.com\"" + extra + " }");
		return mockMvc.perform(token == null ? request : request.header("X-Admin-Token", token));
	}

	@Test
	void hiddenWithoutToken() throws Exception {
		call(null, both()).andExpect(status().isNotFound());
		call("falsch", both() + ", \"send\": true, \"expectedCount\": 2").andExpect(status().isNotFound());
		verify(mail, never()).send(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void previewSendsNothing() throws Exception {
		call(TOKEN, both()).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(false))
				.andExpect(jsonPath("$.count").value(2));
		verify(mail, never()).send(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void sendsEachMailSeparatelyWithReplyTo() throws Exception {
		call(TOKEN, both() + ", \"send\": true, \"expectedCount\": 2").andExpect(status().isOk())
				.andExpect(jsonPath("$.sent").value(true)).andExpect(jsonPath("$.failed").isEmpty());
		verify(mail).send(eq(lina), eq("Neu"), eq("Hallo"), eq("<p>Hallo</p>"), eq("team@example.com"));
		verify(mail).send(eq(ben), eq("Neu"), eq("Hallo"), eq("<p>Hallo</p>"), eq("team@example.com"));
	}

	@Test
	void withoutReplyToAnswersGoToTheFeedbackAddress() throws Exception {
		var request = post("/internal/newsletter").header("X-Admin-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subject\": \"Neu\", \"text\": \"Hallo\", \"html\": \"<p>Hallo</p>\", \"send\": true, "
						+ "\"expectedCount\": 1, \"only\": [\"" + lina + "\"] }");
		mockMvc.perform(request).andExpect(status().isOk())
				.andExpect(jsonPath("$.replyTo").value("betreiber@example.com"));
		verify(mail).send(eq(lina), anyString(), anyString(), anyString(), eq("betreiber@example.com"));
	}

	@Test
	void refusesWhenListDiffersFromPreview() throws Exception {
		call(TOKEN, both() + ", \"send\": true, \"expectedCount\": 5").andExpect(status().isConflict());
		call(TOKEN, both() + ", \"send\": true").andExpect(status().isConflict());
		verify(mail, never()).send(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void testSendOnlyToGivenAddress() throws Exception {
		call(TOKEN, ", \"send\": true, \"expectedCount\": 1, \"only\": [\"" + lina.toUpperCase() + "\"]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
		verify(mail, times(1)).send(anyString(), anyString(), anyString(), anyString(), any());
		verify(mail).send(eq(lina), anyString(), anyString(), anyString(), any());
	}

	@Test
	void excludedAddressesGetNothing() throws Exception {
		call(TOKEN, both() + ", \"exclude\": [\"" + ben + "\"]").andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.recipients[0]").value(lina));
	}

}

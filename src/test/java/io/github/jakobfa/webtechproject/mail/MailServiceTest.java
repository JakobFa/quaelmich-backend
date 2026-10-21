package io.github.jakobfa.webtechproject.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Prüft die Anfrage an Resend gegen einen simulierten Server */
class MailServiceTest {

	private static final MailProperties CONFIGURED = new MailProperties("https://api.resend.com", "re_test",
			"quaelmich <noreply@quaelmich.com>", "https://quaelmich.com");

	@Test
	void sendsTheMailToResend() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer re_test"))
				.andExpect(jsonPath("$.from").value("quaelmich <noreply@quaelmich.com>"))
				.andExpect(jsonPath("$.to[0]").value("lina@example.com"))
				.andExpect(jsonPath("$.subject").value("Hallo"))
				.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		boolean sent = new MailService(CONFIGURED, builder).send("lina@example.com", "Hallo", "Text", "<p>Text</p>");

		assertThat(sent).isTrue();
		server.verify();
	}

	@Test
	void reportsFailureInsteadOfCrashing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

		assertThat(new MailService(CONFIGURED, builder).send("lina@example.com", "Hallo", "Text", "<p>Text</p>"))
				.isFalse();
	}

	@Test
	void withoutKeyTheMailIsOnlyLogged() {
		var unconfigured = new MailProperties("https://api.resend.com", "", "x", "http://localhost:5173");
		assertThat(new MailService(unconfigured).send("lina@example.com", "Hallo", "Text", "<p>Text</p>")).isTrue();
		assertThat(new MailService(unconfigured).frontendUrl("/login")).isEqualTo("http://localhost:5173/login");
	}

}

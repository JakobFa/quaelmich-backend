package io.github.jakobfa.webtechproject.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Fehlt REGISTRATION_INVITE_ONLY (z. B. beim Neuaufsetzen), bleibt die Registrierung trotzdem zu */
class RegistrationDefaultTest {

	@Test
	void registrationNeedsAnInvitationUnlessExplicitlyOpened() throws Exception {
		// im Test-Classpath liegt die Test-Konfiguration vorn – deshalb die Haupt-Konfiguration direkt lesen
		Properties main = new Properties();
		try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
			main.load(in);
		}

		assertThat(main.getProperty("app.registration.invite-only")).isEqualTo("${REGISTRATION_INVITE_ONLY:true}");
	}

}

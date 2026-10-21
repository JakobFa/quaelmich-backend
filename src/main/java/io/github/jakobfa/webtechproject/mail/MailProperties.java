package io.github.jakobfa.webtechproject.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail-Versand über Resend.
 *
 * @param apiKey          API-Schlüssel (nur Senden) – leer = Mails werden nur ins Log geschrieben
 * @param from            Absender, z. B. "quaelmich <noreply@quaelmich.com>"
 * @param frontendBaseUrl Adresse der App für Links in Mails, z. B. "https://quaelmich.com"
 */
@ConfigurationProperties("app.mail")
public record MailProperties(String apiBaseUrl, String apiKey, String from, String frontendBaseUrl) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

}

package io.github.jakobfa.webtechproject.integration.strava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen der Strava-App (angelegt unter strava.com/settings/api).
 *
 * @param baseUrl      Adresse von Strava
 * @param clientId     öffentliche ID der Strava-App
 * @param clientSecret Geheimnis der Strava-App – nur als Umgebungsvariable setzen
 */
@ConfigurationProperties("app.strava")
public record StravaProperties(String baseUrl, String clientId, String clientSecret) {

	/** Ohne Client-ID und -Secret ist die Strava-Anbindung ausgeschaltet */
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}

}

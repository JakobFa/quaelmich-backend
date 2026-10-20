package io.github.jakobfa.webtechproject.integration.intervals;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen für die intervals.icu-Anbindung aus application.properties bzw. Umgebungsvariablen.
 *
 * @param baseUrl                    Adresse der intervals.icu-API
 * @param apiKey                     persönlicher API-Schlüssel (nur als Umgebungsvariable setzen!)
 * @param athleteId                  intervals.icu-Athleten-ID, 0 = Besitzer des Schlüssels
 * @param allowedQuaelmichAthleteId  das einzige quaelmich-Profil, das in den Kalender schreiben darf
 */
@ConfigurationProperties("app.intervals")
public record IntervalsProperties(String baseUrl, String apiKey, String athleteId, Long allowedQuaelmichAthleteId) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** Ohne Login schützt diese Prüfung davor, dass fremde Besucher in den eigenen Kalender schreiben. */
	public boolean allows(Long quaelmichAthleteId) {
		return isConfigured() && allowedQuaelmichAthleteId != null && allowedQuaelmichAthleteId.equals(quaelmichAthleteId);
	}

}

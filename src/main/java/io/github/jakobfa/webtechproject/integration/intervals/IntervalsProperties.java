package io.github.jakobfa.webtechproject.integration.intervals;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen für die intervals.icu-Anbindung aus application.properties bzw. Umgebungsvariablen.
 *
 * @param baseUrl       Adresse der intervals.icu-API
 * @param encryptionKey Schlüssel (Base64, 32 Byte) zum Verschlüsseln der API-Schlüssel der Nutzer
 */
@ConfigurationProperties("app.intervals")
public record IntervalsProperties(String baseUrl, String encryptionKey) {
}

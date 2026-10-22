package io.github.jakobfa.webtechproject.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsProperties;

/**
 * Prüft das Geheimnis INTERVALS_SYNC_TOKEN im Header "X-Sync-Token" für interne Routen
 * (nächtlicher Sync, Einladungscodes). Nur der Betreiber kennt es.
 */
@Component
public class InternalAccess {

	private final IntervalsProperties properties;

	public InternalAccess(IntervalsProperties properties) {
		this.properties = properties;
	}

	/** Ohne konfiguriertes Geheimnis gibt es die internen Routen gar nicht */
	public boolean isEnabled() {
		return properties.syncToken() != null && !properties.syncToken().isBlank();
	}

	public boolean accepts(String token) {
		return isEnabled() && token != null && constantTimeEquals(token, properties.syncToken());
	}

	/** Vergleich in konstanter Zeit, damit das Geheimnis nicht über Antwortzeiten erraten werden kann */
	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}

package io.github.jakobfa.webtechproject.integration.intervals;

/**
 * Entschlüsselte Zugangsdaten eines Nutzers – nur im Arbeitsspeicher, nie gespeichert oder ausgegeben.
 */
public record IntervalsCredentials(String apiKey, String athleteId) {

	@Override
	public String toString() {
		// verhindert, dass der Schlüssel versehentlich in Logs landet
		return "IntervalsCredentials[athleteId=" + athleteId + ", apiKey=***]";
	}

}

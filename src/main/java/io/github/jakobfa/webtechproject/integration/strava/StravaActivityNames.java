package io.github.jakobfa.webtechproject.integration.strava;

import java.util.regex.Pattern;

/**
 * Erkennt die Namen, die Strava selbst vergibt ("Radfahrt am Abend", "Evening Ride"). Nur diese dürfen
 * durch den Namen des Trainings ersetzt werden – alles andere hat der Nutzer selbst geschrieben.
 */
public final class StravaActivityNames {

	/** Deutsch: "Radfahrt am Abend", "Virtuelle Radfahrt am Morgen", "Fahrt am Nachmittag", "Radfahrt in der Nacht" */
	private static final Pattern GERMAN = Pattern.compile(
			"^(?:[\\p{L}\\-]+ )*(?:am (?:Morgen|Vormittag|Mittag|Nachmittag|Abend)|in der Nacht)$");

	/** Englisch: "Morning Ride", "Evening Virtual Ride", "Afternoon Gravel Ride" */
	private static final Pattern ENGLISH = Pattern.compile(
			"^(?:Morning|Lunch|Afternoon|Evening|Night) (?:[\\p{L}\\-]+ )*Ride$");

	private StravaActivityNames() {
	}

	/** Leer oder ein Standardname von Strava? */
	public static boolean isDefault(String name) {
		if (name == null || name.isBlank()) {
			return true;
		}
		String trimmed = name.strip();
		return GERMAN.matcher(trimmed).matches() || ENGLISH.matcher(trimmed).matches();
	}
}

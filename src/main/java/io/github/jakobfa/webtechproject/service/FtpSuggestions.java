package io.github.jakobfa.webtechproject.service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.model.Athlete;

/**
 * Erkennt, wenn die eingetragene FTP deutlich zu niedrig ist – nur bei sehr sicheren Fällen.
 *
 * Die FTP ist die Leistung, die man etwa eine Stunde halten kann. Wer mit echtem Wattmesser 60 Minuten oder
 * länger mit einer Normalized Power fährt, die mindestens 8 % über der FTP liegt, hat sicher eine höhere FTP.
 * Bei 40–60 Minuten wird vorsichtig mit 95 % gerechnet (kürzer geht immer etwas mehr).
 * Puls allein reicht dafür nicht: Hitze, Müdigkeit oder Koffein verschieben ihn zu stark.
 * Zu hoch lässt sich die FTP so nicht sicher erkennen – dafür gibt es die regelmäßigen FTP-Tests.
 */
public final class FtpSuggestions {

	static final int MIN_MINUTES = 40;
	static final int FULL_HOUR = 60;
	/** erst ab 8 % Abweichung – kleine Unterschiede sind normale Tagesform */
	static final double MARGIN = 1.08;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN);

	private FtpSuggestions() {
	}

	/** Aktualisiert den Hinweis am Profil anhand der Fahrten */
	public static void evaluate(Athlete athlete, List<RecordedActivity> rides) {
		Integer ftp = athlete.getFtp();
		if (ftp == null) {
			return;
		}
		Integer suggested = athlete.getSuggestedFtp();
		boolean fromTest = Boolean.TRUE.equals(athlete.getSuggestedFtpFromTest());
		// inzwischen angepasst (ein Test-Ergebnis darf auch niedriger sein – dann erst bei Gleichstand erledigt)
		if (suggested != null && (fromTest ? ftp.equals(suggested) : ftp >= suggested)) {
			clear(athlete);
		}
		best(rides).filter(estimate -> estimate.watts() >= ftp * MARGIN)
				.filter(estimate -> athlete.getSuggestedFtp() == null || estimate.watts() > athlete.getSuggestedFtp())
				.ifPresent(estimate -> {
					athlete.setSuggestedFtp(estimate.watts());
					athlete.setSuggestedFtpNote(estimate.note());
					athlete.setSuggestedFtpFromTest(false);
				});
	}

	/** Ein Rampentest wurde erkannt: sein Ergebnis wird vorgeschlagen – auch niedriger, ein Test ist ein Test */
	public static void fromRampTest(Athlete athlete, RampTest.Result result, java.time.LocalDate date) {
		if (athlete.getFtp() != null && athlete.getFtp() == result.ftp()) {
			clear(athlete);
			return;
		}
		athlete.setSuggestedFtp(result.ftp());
		athlete.setSuggestedFtpNote("Dein Rampentest am %s: beste Minute %d W, davon 75 %% sind %d W."
				.formatted(DAY.format(date), result.bestMinuteWatts(), result.ftp()));
		athlete.setSuggestedFtpFromTest(true);
	}

	public static void clear(Athlete athlete) {
		athlete.setSuggestedFtp(null);
		athlete.setSuggestedFtpNote(null);
		athlete.setSuggestedFtpFromTest(null);
	}

	record Estimate(int watts, String note) {
	}

	static Optional<Estimate> best(List<RecordedActivity> rides) {
		return rides.stream()
				.filter(RecordedActivity::isRide)
				.filter(ride -> ride.normalizedPower() != null && ride.movingMinutes() >= MIN_MINUTES)
				.map(ride -> {
					int watts = ride.movingMinutes() >= FULL_HOUR ? ride.normalizedPower()
							: (int) Math.round(ride.normalizedPower() * 0.95);
					return new Estimate(watts, "Am %s bist du %d min mit %d W Normalized Power gefahren."
							.formatted(DAY.format(ride.date()), ride.movingMinutes(), ride.normalizedPower()));
				})
				.max(Comparator.comparingInt(Estimate::watts));
	}

}

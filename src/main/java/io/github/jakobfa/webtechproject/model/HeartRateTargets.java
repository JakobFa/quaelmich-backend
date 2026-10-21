package io.github.jakobfa.webtechproject.model;

import java.util.List;

/**
 * Übersetzt die Leistung eines Workout-Abschnitts (% FTP) in einen Pulsbereich für Training ohne Wattmesser.
 *
 * Die Leistungszonen nach Coggan werden auf die 5 Pulszonen abgebildet:
 * unter 56 % → Zone 1, bis 75 % → Zone 2, bis 90 % → Zone 3, bis 105 % → Zone 4, darüber → Zone 5.
 * Sehr kurze, harte Abschnitte bekommen kein Pulsziel – dafür reagiert der Puls zu langsam.
 *
 * @param zoneStarts   Beginn von Zone 2–5 in bpm
 * @param maxHeartRate Obergrenze von Zone 5
 */
public record HeartRateTargets(List<Integer> zoneStarts, int maxHeartRate) {

	/** Untergrenze für Zone 1, damit Radcomputer bei lockerem Ausrollen nicht piepen */
	private static final int ZONE_ONE_FLOOR = 60;
	/** Kürzere harte Abschnitte werden nach Gefühl gefahren */
	public static final int MIN_SECONDS_FOR_TARGET = 120;

	/** Ein Pulsbereich; zone = 1 … 5 */
	public record Range(int zone, int low, int high) {
	}

	/** Zone 1–5 für eine Leistung in % FTP */
	public static int zoneFor(double powerPercent) {
		if (powerPercent < 56) {
			return 1;
		}
		if (powerPercent < 76) {
			return 2;
		}
		if (powerPercent < 91) {
			return 3;
		}
		if (powerPercent < 106) {
			return 4;
		}
		return 5;
	}

	/** Pulsbereich eines Abschnitts; null bei kurzen, harten Abschnitten (dann nach Gefühl) */
	public Range forStep(WorkoutStep step) {
		double averagePercent = (step.powerStartPercent() + step.powerEndPercent()) / 2.0;
		int zone = zoneFor(averagePercent);
		if (zone == 5 && step.durationSeconds() < MIN_SECONDS_FOR_TARGET) {
			return null;
		}
		return range(zone);
	}

	public Range range(int zone) {
		int low = zone == 1 ? ZONE_ONE_FLOOR : zoneStarts.get(zone - 2);
		int high = zone == 5 ? maxHeartRate : zoneStarts.get(zone - 1) - 1;
		return new Range(zone, low, high);
	}

}

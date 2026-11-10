package io.github.jakobfa.webtechproject.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ein strukturiertes Workout, bestehend aus mehreren Abschnitten.
 * Dauer und Trainingsstress werden aus den Abschnitten berechnet.
 */
@Getter
@AllArgsConstructor
public class Workout {

	private Long id;
	private String title;
	private WorkoutType type;
	private String description;
	private List<WorkoutStep> steps;

	public int getDurationInMinutes() {
		int seconds = steps.stream().mapToInt(WorkoutStep::durationSeconds).sum();
		return Math.round(seconds / 60f);
	}

	/**
	 * Geplante Trainingslast, gerechnet wie bei einer gefahrenen Fahrt: mit der Normalized Power (30-Sekunden-Mittel,
	 * vierte Potenz) statt mit dem einfachen Mittel. Sonst sähe jede perfekt gefahrene Intervalleinheit "härter als
	 * geplant" aus: Wechsel zwischen hart und locker zählen bei der Normalized Power mehr als im Mittel (VO2max 4 × 4:
	 * 53 statt 64 TSS bei genau getroffenen Intervallen). Bei gleichmäßigen Einheiten ändert sich nichts.
	 */
	public int getTss() {
		// der Generator fragt die Last tausendfach ab, die Berechnung läuft pro Abschnittsfolge nur einmal
		if (TSS_CACHE.size() > MAX_CACHED) {
			TSS_CACHE.clear();
		}
		return TSS_CACHE.computeIfAbsent(steps, Workout::computeTss);
	}

	private static final java.util.Map<List<WorkoutStep>, Integer> TSS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int MAX_CACHED = 20_000;

	private static int computeTss(List<WorkoutStep> steps) {
		int seconds = steps.stream().mapToInt(WorkoutStep::durationSeconds).sum();
		if (seconds <= 0) {
			return 0;
		}
		double[] power = new double[seconds];
		int index = 0;
		for (WorkoutStep step : steps) {
			double start = step.powerStartPercent() / 100.0;
			double end = step.powerEndPercent() / 100.0;
			int length = step.durationSeconds();
			for (int second = 0; second < length; second++) {
				// Mitte des jeweiligen Sekundenintervalls auf der Rampe
				power[index++] = length == 0 ? start : start + (end - start) * (second + 0.5) / length;
			}
		}
		final int window = 30;
		double windowSum = 0;
		double fourthPowerSum = 0;
		for (int i = 0; i < seconds; i++) {
			windowSum += power[i];
			if (i >= window) {
				windowSum -= power[i - window];
			}
			double rolling = windowSum / Math.min(i + 1, window);
			fourthPowerSum += rolling * rolling * rolling * rolling;
		}
		double normalizedPower = Math.sqrt(Math.sqrt(fourthPowerSum / seconds));
		return (int) Math.round(seconds / 3600.0 * normalizedPower * normalizedPower * 100);
	}

}

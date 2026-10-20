package io.github.jakobfa.webtechproject.model;

/**
 * Ein Abschnitt eines Workouts mit Dauer und Zielleistung in Prozent der FTP.
 * Unterscheiden sich Start- und Endwert, steigt bzw. fällt die Leistung linear (Rampe).
 */
public record WorkoutStep(StepKind kind, int durationSeconds, int powerStartPercent, int powerEndPercent) {

	public static WorkoutStep steady(StepKind kind, int durationSeconds, int powerPercent) {
		return new WorkoutStep(kind, durationSeconds, powerPercent, powerPercent);
	}

	public static WorkoutStep ramp(StepKind kind, int durationSeconds, int fromPercent, int toPercent) {
		return new WorkoutStep(kind, durationSeconds, fromPercent, toPercent);
	}

	/**
	 * Training Stress Score dieses Abschnitts: Stunden × Intensität² × 100.
	 * Bei einer Rampe von a nach b ist der Mittelwert von Intensität² gleich (a² + ab + b²) / 3.
	 */
	public double stressScore() {
		double hours = durationSeconds / 3600.0;
		double start = powerStartPercent / 100.0;
		double end = powerEndPercent / 100.0;
		double meanIntensitySquared = (start * start + start * end + end * end) / 3;
		return hours * meanIntensitySquared * 100;
	}

}

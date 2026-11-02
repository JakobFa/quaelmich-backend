package io.github.jakobfa.webtechproject.integration;

/**
 * Die zentrale Belastungsrechnung von quaelmich – für alle Quellen gleich, immer mit den Werten aus dem
 * eigenen Profil (FTP, Schwellenpuls). So hängt der TSS nicht davon ab, was in Strava oder intervals.icu
 * eingestellt ist.
 */
public final class TrainingLoad {

	private TrainingLoad() {
	}

	/**
	 * TSS = Stunden × IF² × 100, mit IF = Normalized Power / FTP.
	 * Null ohne echten Wattmesser (geschätzte Watt zählen nicht) oder ohne FTP.
	 */
	public static Integer powerTss(int movingSeconds, Integer normalizedPower, Boolean deviceWatts, Integer ftp) {
		if (normalizedPower == null || !Boolean.TRUE.equals(deviceWatts) || ftp == null || ftp <= 0) {
			return null;
		}
		double intensity = (double) normalizedPower / ftp;
		return (int) Math.round(movingSeconds / 3600.0 * intensity * intensity * 100);
	}

	/** Typischer Ruhepuls – der Puls startet nicht bei null, sondern ungefähr hier */
	static final int RESTING_HEART_RATE = 60;

	/**
	 * hrTSS = Stunden × IF² × 100 wie beim Watt-TSS, mit der Intensität aus der Herzfrequenz-Reserve:
	 * IF = (Ø-Puls − Ruhepuls) / (Schwellenpuls − Ruhepuls). Ohne den Ruhepuls würden lockere Fahrten
	 * stark überschätzt (120 bpm bei 170 Schwelle wären sonst IF 0,71 statt realistisch ~0,55).
	 * Mit dem Durchschnitt wird ein Intervalltraining eher unterschätzt; fürs Pendeln und lockere Fahrten
	 * passt es gut. Null ohne Pulsdaten oder ohne Schwellenpuls.
	 */
	public static Integer heartRateTss(int movingSeconds, Double averageHeartRate, Integer thresholdHeartRate) {
		if (averageHeartRate == null || averageHeartRate <= 0 || thresholdHeartRate == null
				|| thresholdHeartRate <= RESTING_HEART_RATE) {
			return null;
		}
		double intensity = Math.max(0, averageHeartRate - RESTING_HEART_RATE)
				/ (thresholdHeartRate - RESTING_HEART_RATE);
		return (int) Math.round(movingSeconds / 3600.0 * intensity * intensity * 100);
	}

	/** Mehr als diesen Faktor gibt es nicht: Pausen und Ausreißer sollen den TSS nicht aufblasen */
	static final double MAX_PULSE_VARIABILITY = 1.25;

	/**
	 * Korrektur für den Puls-TSS bei unruhigen Fahrten: Der Durchschnittspuls unterschätzt, weil die Last mit dem
	 * Quadrat der Intensität wächst. Der Faktor ist Mittel(IF²) / Mittel(IF)² über die Pulswerte der Fahrt
	 * (mindestens 1, höchstens {@value #MAX_PULSE_VARIABILITY}) und wird mit dem Durchschnitts-TSS multipliziert.
	 * Leer ohne brauchbare Pulswerte.
	 */
	public static java.util.OptionalDouble pulseVariability(Integer[] heartRate, Integer thresholdHeartRate) {
		if (thresholdHeartRate == null || thresholdHeartRate <= RESTING_HEART_RATE) {
			return java.util.OptionalDouble.empty();
		}
		double sum = 0;
		double sumOfSquares = 0;
		int count = 0;
		for (Integer value : heartRate) {
			if (value == null || value <= 0) {
				continue;
			}
			double intensity = Math.max(0, value - RESTING_HEART_RATE) / (double) (thresholdHeartRate - RESTING_HEART_RATE);
			sum += intensity;
			sumOfSquares += intensity * intensity;
			count++;
		}
		if (count < 60 || sum <= 0) {
			return java.util.OptionalDouble.empty();
		}
		double mean = sum / count;
		double factor = (sumOfSquares / count) / (mean * mean);
		return java.util.OptionalDouble.of(Math.clamp(factor, 1.0, MAX_PULSE_VARIABILITY));
	}

}

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

	/**
	 * hrTSS = Stunden × (Ø-Puls / Schwellenpuls)² × 100 – dieselbe Formel wie beim Watt-TSS, nur mit Puls.
	 * Mit dem Durchschnitt statt Pulszonen wird ein Intervalltraining eher unterschätzt; fürs Pendeln
	 * oder lockere Fahrten passt es gut. Null ohne Pulsdaten oder ohne Schwellenpuls.
	 */
	public static Integer heartRateTss(int movingSeconds, Double averageHeartRate, Integer thresholdHeartRate) {
		if (averageHeartRate == null || averageHeartRate <= 0 || thresholdHeartRate == null
				|| thresholdHeartRate <= 0) {
			return null;
		}
		double intensity = averageHeartRate / thresholdHeartRate;
		return (int) Math.round(movingSeconds / 3600.0 * intensity * intensity * 100);
	}

}

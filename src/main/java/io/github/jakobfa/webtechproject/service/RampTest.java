package io.github.jakobfa.webtechproject.service;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Wertet einen Rampentest aus: jede Minute etwas mehr Watt, bis nichts mehr geht. Die FTP sind 75 % der besten
 * Minute – so rechnen auch Zwift, TrainerRoad und Wahoo.
 *
 * Damit nicht jede Fahrt mit einer harten Minute als Test gilt, muss vor der besten Minute eine echte Rampe
 * liegen: in den 8 Minuten davor steigt die Leistung fast jede Minute und am Ende um mindestens ein Viertel.
 */
public final class RampTest {

	/** FTP = so viel der besten Minute */
	static final double FTP_SHARE_OF_BEST_MINUTE = 0.75;
	static final int MINUTE = 60;
	/** so viele Minuten vor der besten Minute müssen ansteigen */
	static final int RAMP_MINUTES = 8;
	/** von diesen Minutenwechseln dürfen höchstens so viele nicht steigen (Schwankung, kurz Luft holen) */
	static final int ALLOWED_DIPS = 1;
	/** die Rampe muss über diese Minuten mindestens so viel steigen */
	static final double MIN_RISE = 1.25;

	public record Result(int bestMinuteWatts, int ftp) {
	}

	private RampTest() {
	}

	/**
	 * @param watts eine Zahl je Sekunde, null = Pause (wird übersprungen, so zählt nur gefahrene Zeit)
	 * @return leer, wenn das kein Rampentest war
	 */
	public static Optional<Result> evaluate(Integer[] watts) {
		int[] riding = Arrays.stream(watts).filter(Objects::nonNull).mapToInt(Integer::intValue).toArray();
		int needed = (RAMP_MINUTES + 1) * MINUTE;
		if (riding.length < needed) {
			return Optional.empty();
		}
		long[] sums = new long[riding.length + 1];
		for (int i = 0; i < riding.length; i++) {
			sums[i + 1] = sums[i] + riding[i];
		}
		// Ende der besten Minute (frühestens dort, wo davor noch die ganze Rampe Platz hat)
		int bestEnd = -1;
		long bestSum = -1;
		for (int end = needed; end <= riding.length; end++) {
			long sum = sums[end] - sums[end - MINUTE];
			if (sum > bestSum) {
				bestSum = sum;
				bestEnd = end;
			}
		}
		if (!rampBefore(sums, bestEnd)) {
			return Optional.empty();
		}
		int best = (int) Math.round(bestSum / (double) MINUTE);
		return Optional.of(new Result(best, (int) Math.round(best * FTP_SHARE_OF_BEST_MINUTE)));
	}

	/** Steigt die Leistung in den Minuten vor end fast durchgehend? */
	private static boolean rampBefore(long[] sums, int end) {
		double[] minutes = new double[RAMP_MINUTES + 1];
		for (int i = 0; i <= RAMP_MINUTES; i++) {
			int to = end - (RAMP_MINUTES - i) * MINUTE;
			minutes[i] = (sums[to] - sums[to - MINUTE]) / (double) MINUTE;
		}
		int dips = 0;
		for (int i = 1; i < minutes.length; i++) {
			if (minutes[i] <= minutes[i - 1]) {
				dips++;
			}
		}
		return dips <= ALLOWED_DIPS && minutes[0] > 0 && minutes[RAMP_MINUTES] >= minutes[0] * MIN_RISE;
	}

}

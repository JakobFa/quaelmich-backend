package io.github.jakobfa.webtechproject.integration;

import java.util.List;

/**
 * Sekundengenaue Werte einer Fahrt: Index = Sekunde seit dem Start. Fehlende Sekunden (Pausen) sind null.
 */
public record ActivityStream(Integer[] watts, Integer[] heartRate) {

	/** Baut aus "Zeit in Sekunden" und den zugehörigen Werten einen Strom mit einem Wert pro Sekunde */
	public static ActivityStream of(List<? extends Number> time, List<? extends Number> watts,
			List<? extends Number> heartRate) {
		if (time == null || time.isEmpty()) {
			return new ActivityStream(new Integer[0], new Integer[0]);
		}
		int length = time.getLast().intValue() + 1;
		return new ActivityStream(spread(time, watts, length), spread(time, heartRate, length));
	}

	private static Integer[] spread(List<? extends Number> time, List<? extends Number> values, int length) {
		Integer[] result = new Integer[length];
		if (values == null || values.size() != time.size()) {
			return result;
		}
		for (int i = 0; i < time.size(); i++) {
			Number value = values.get(i);
			int second = time.get(i).intValue();
			if (value != null && second >= 0 && second < length) {
				result[second] = (int) Math.round(value.doubleValue());
			}
		}
		return result;
	}

	public boolean hasWatts() {
		return coverage(watts) > 0.5;
	}

	public boolean hasHeartRate() {
		return coverage(heartRate) > 0.5;
	}

	private static double coverage(Integer[] values) {
		if (values.length == 0) {
			return 0;
		}
		long known = java.util.Arrays.stream(values).filter(java.util.Objects::nonNull).count();
		return (double) known / values.length;
	}

}

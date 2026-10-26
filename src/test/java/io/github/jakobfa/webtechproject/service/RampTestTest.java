package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** Rampentest erkennen und die FTP daraus ausrechnen */
class RampTestTest {

	private final Random noise = new Random(7);

	/** seconds Sekunden mit ungefähr watts (±8 W, wie echte Wattmesser) */
	private void ride(List<Integer> stream, int seconds, int watts) {
		for (int i = 0; i < seconds; i++) {
			stream.add(Math.max(0, watts + noise.nextInt(17) - 8));
		}
	}

	/** Rampe wie in der App: ab 150 W jede Minute +15 W, aufgehört nach der Minute mit lastWatts */
	private Integer[] rampTest(int lastWatts) {
		List<Integer> stream = new ArrayList<>();
		ride(stream, 8 * 60, 130); // Aufwärmen
		for (int watts = 150; watts <= lastWatts; watts += 15) {
			ride(stream, 60, watts);
		}
		ride(stream, 10 * 60, 110); // Ausfahren
		return stream.toArray(Integer[]::new);
	}

	@Test
	void ftpIsThreeQuartersOfTheBestMinute() {
		var result = RampTest.evaluate(rampTest(360)).orElseThrow();

		assertThat(result.bestMinuteWatts()).isBetween(357, 363);
		assertThat(result.ftp()).isBetween(268, 272);
	}

	@Test
	void pausesDoNotBreakTheRamp() {
		List<Integer> stream = new ArrayList<>(List.of(rampTest(330)));
		for (int i = 0; i < 30; i++) {
			stream.add(9 * 60, null); // kurz angehalten, z. B. Trainer neu verbunden
		}

		assertThat(RampTest.evaluate(stream.toArray(Integer[]::new))).isPresent();
	}

	@Test
	void anIntervalWorkoutIsNoRampTest() {
		List<Integer> vo2max = new ArrayList<>();
		ride(vo2max, 10 * 60, 140);
		for (int i = 0; i < 5; i++) {
			ride(vo2max, 4 * 60, 300);
			ride(vo2max, 4 * 60, 120);
		}
		ride(vo2max, 10 * 60, 120);

		assertThat(RampTest.evaluate(vo2max.toArray(Integer[]::new))).isEmpty();
	}

	@Test
	void aSteadyRideIsNoRampTest() {
		List<Integer> steady = new ArrayList<>();
		ride(steady, 60 * 60, 200);

		assertThat(RampTest.evaluate(steady.toArray(Integer[]::new))).isEmpty();
	}

	@Test
	void tooShortIsNoTest() {
		List<Integer> stream = new ArrayList<>();
		ride(stream, 5 * 60, 200);

		assertThat(RampTest.evaluate(stream.toArray(Integer[]::new))).isEmpty();
	}

}

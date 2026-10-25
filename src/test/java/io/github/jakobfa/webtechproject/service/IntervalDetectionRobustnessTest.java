package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.integration.ActivityStream;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;

/**
 * Belastungstest für die Intervall-Erkennung: viele zufällige, aber realistische Fahrten draußen – Anfahrt mit
 * Ampeln und Anstiegen, Einrollen übersprungen, längere Pausen, Schwankungen, Heimweg. Für jede Fahrt sind die
 * echten Startpunkte bekannt; die Erkennung muss sie finden. Fester Zufallsstartwert, damit der Test immer
 * gleich läuft.
 */
class IntervalDetectionRobustnessTest {

	private static final int FTP = 250;
	private static final int RIDES_PER_WORKOUT = 25;
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();

	/** Eine erzeugte Fahrt: Sekundenwerte (null = Stillstand) und die echten Startsekunden der Blöcke */
	record Ride(List<Integer> time, List<Integer> watts, List<Integer> blockStarts) {
	}

	private static final class Builder {
		final Random random;
		final List<Integer> time = new ArrayList<>();
		final List<Integer> watts = new ArrayList<>();
		int second = 0;

		Builder(Random random) {
			this.random = random;
		}

		void ride(int seconds, double percent, double noise) {
			for (int i = 0; i < seconds; i++) {
				time.add(second++);
				double value = percent / 100 * FTP * (1 + random.nextGaussian() * noise);
				watts.add((int) Math.max(0, Math.round(value)));
			}
		}

		void stop(int seconds) {
			second += seconds;
		}

		int between(int min, int max) {
			return min + random.nextInt(max - min + 1);
		}
	}

	static Ride generate(Workout workout, Random random) {
		Builder b = new Builder(random);
		// Anfahrt: wechselndes Tempo, Ampeln, manchmal ein Anstieg
		int approach = b.between(0, 40 * 60);
		while (b.second < approach) {
			int roll = b.between(0, 9);
			if (roll == 0) {
				b.stop(b.between(20, 180));
			} else if (roll == 1) {
				b.ride(b.between(3 * 60, 8 * 60), b.between(85, 100), 0.06);
			} else {
				b.ride(b.between(60, 6 * 60), b.between(50, 80), 0.12);
			}
		}

		List<Integer> starts = new ArrayList<>();
		List<WorkoutStep> steps = workout.getSteps();
		boolean inBlock = false;
		double bias = 0;
		for (WorkoutStep step : steps) {
			double percent = (step.powerStartPercent() + step.powerEndPercent()) / 2.0;
			if (step.kind() == StepKind.INTERVAL) {
				if (!inBlock) {
					starts.add(b.second);
					bias = random.nextGaussian() * 0.04; // mal etwas zu hart, mal zu leicht
					inBlock = true;
				}
				b.ride(step.durationSeconds(), percent * (1 + bias), 0.05);
				continue;
			}
			inBlock = false;
			switch (step.kind()) {
				// Einrollen gekürzt oder übersprungen (schon auf der Anfahrt erledigt)
				case WARMUP -> b.ride((int) (step.durationSeconds() * random.nextDouble()), percent, 0.1);
				case RECOVERY -> {
					int seconds = step.durationSeconds();
					if (seconds >= 60 && random.nextInt(4) == 0) {
						int stop = b.between(15, Math.min(90, seconds - 30));
						b.ride((seconds - stop) / 2, percent, 0.1);
						b.stop(stop);
						b.ride(seconds - stop - (seconds - stop) / 2, percent, 0.1);
					} else {
						b.ride(seconds + (random.nextInt(3) == 0 ? b.between(0, Math.min(60, seconds / 2)) : 0),
								percent, 0.1);
					}
				}
				default -> b.ride(step.durationSeconds(), percent, 0.1);
			}
		}
		// Heimweg
		b.ride(b.between(5 * 60, 40 * 60), b.between(50, 75), 0.12);
		return new Ride(b.time, b.watts, starts);
	}

	@Test
	void findsTheIntervalsInManyRealisticOutdoorRides() {
		Random random = new Random(20261025L);
		List<Workout> workouts = LIBRARY.findAll().stream()
				.filter(workout -> !IntervalAccuracy.blocksOf(workout).isEmpty()).toList();
		assertThat(workouts).hasSizeGreaterThan(10);

		List<String> misses = new ArrayList<>();
		int rides = 0;
		for (Workout workout : workouts) {
			for (int n = 0; n < RIDES_PER_WORKOUT; n++) {
				Ride ride = generate(workout, random);
				rides++;
				var result = IntervalAccuracy.byPower(workout, ActivityStream.of(ride.time(), ride.watts(), null), FTP);
				if (result.isEmpty()) {
					misses.add(workout.getTitle() + " #" + n + ": nicht ausgewertet");
					continue;
				}
				var blocks = result.get().blocks();
				for (int i = 0; i < blocks.size(); i++) {
					int found = blocks.get(i).startSecond();
					int truth = ride.blockStarts().get(i);
					int tolerance = Math.max(15, Math.min(60, blocks.get(i).durationSeconds() / 4));
					if (Math.abs(found - truth) > tolerance) {
						misses.add("%s #%d Block %d: gefunden %d s, echt %d s".formatted(workout.getTitle(), n, i + 1,
								found, truth));
					}
				}
			}
		}
		long failedRides = misses.stream().map(miss -> miss.substring(0, miss.indexOf(miss.contains(" Block") ? " Block" : ":")))
				.distinct().count();
		System.out.println(rides + " Fahrten, davon " + failedRides + " nicht richtig erkannt");
		misses.forEach(System.out::println);
		// Grenze: Tempo bei 85 % mitten in einer langen Grundlage (68 %) hebt sich kaum ab – liegen auf der
		// Anfahrt mehrere harte Anstiege, ist das auch für einen Menschen nicht eindeutig. Mehr als 1 % darf es
		// aber nicht werden.
		assertThat(failedRides).as("nicht erkannte Fahrten von %d", rides).isLessThanOrEqualTo(rides / 100);
		assertThat(misses).as("kurze und harte Intervalle immer richtig")
				.allMatch(miss -> miss.startsWith("Lange Ausfahrt") || miss.startsWith("Tempo"));
	}

}

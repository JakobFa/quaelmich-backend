package io.github.jakobfa.webtechproject.service;

import static io.github.jakobfa.webtechproject.model.StepKind.COOLDOWN;
import static io.github.jakobfa.webtechproject.model.StepKind.INTERVAL;
import static io.github.jakobfa.webtechproject.model.StepKind.RECOVERY;
import static io.github.jakobfa.webtechproject.model.StepKind.STEADY;
import static io.github.jakobfa.webtechproject.model.StepKind.WARMUP;
import static io.github.jakobfa.webtechproject.model.WorkoutStep.ramp;
import static io.github.jakobfa.webtechproject.model.WorkoutStep.steady;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Bibliothek klassischer Rad-Workouts. Leistungsangaben in Prozent der FTP.
 */
@Service
public class WorkoutLibrary {

	/**
	 * Alle Workouts. Innerhalb einer Trainingsart sind die Varianten nach steigender Belastung sortiert –
	 * der Plan-Generator steigert sich so von Woche zu Woche. Die IDs bleiben stabil, weil geplante Workouts
	 * in der Datenbank darauf verweisen.
	 */
	private final List<Workout> workouts = List.of(
			recovery(45),

			endurance(90),

			longRide(180),

			// ---------- Tempo ----------
			new Workout(12L, "Tempo 2 × 15", WorkoutType.TEMPO,
					"Zwei Blöcke knapp unter der Schwelle – der Einstieg ins Tempotraining.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(15), 82), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(4L, "Tempo 2 × 20", WorkoutType.TEMPO,
					"Zwei lange Blöcke knapp unter der Schwelle für muskuläre Ausdauer.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(20), 83), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(13L, "Tempo 3 × 20", WorkoutType.TEMPO,
					"Eine Stunde Tempo in drei Blöcken – viel Umfang knapp unter der Schwelle.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(20), 85), steady(RECOVERY, min(5), 55)),
							cooldown())),

			// ---------- Sweet Spot ----------
			new Workout(14L, "Sweet Spot 3 × 10", WorkoutType.SWEET_SPOT,
					"Drei kurze Sweet-Spot-Blöcke zum Reinkommen.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(10), 89), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(5L, "Sweet Spot 3 × 12", WorkoutType.SWEET_SPOT,
					"Viel Trainingswirkung bei überschaubarer Ermüdung – der Klassiker für wenig Zeit.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(12), 90), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(16L, "Sweet Spot 2 × 20", WorkoutType.SWEET_SPOT,
					"Zwei lange Blöcke am oberen Sweet Spot – weniger Pausen als bei 3 × 12.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(20), 92), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(15L, "Sweet Spot 3 × 15", WorkoutType.SWEET_SPOT,
					"45 Minuten Sweet Spot – die umfangreichste Variante, die Brücke zum Schwellentraining.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(15), 90), steady(RECOVERY, min(5), 55)),
							cooldown())),

			// ---------- Schwelle ----------
			new Workout(18L, "Schwelle 2 × 15", WorkoutType.THRESHOLD,
					"Zwei Blöcke an der FTP mit großzügiger Pause – der Einstieg ins Schwellentraining.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(15), 97), steady(RECOVERY, min(7), 55)),
							cooldown())),
			new Workout(17L, "Schwelle 3 × 10", WorkoutType.THRESHOLD,
					"Drei Blöcke an der FTP – etwas mehr Zeit an der Schwelle als bei 2 × 15.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(10), 98), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(6L, "Schwelle 2 × 20", WorkoutType.THRESHOLD,
					"Zwei Blöcke an der FTP. Hart, aber genau das hebt die Schwelle an.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(20), 97), steady(RECOVERY, min(8), 55)),
							cooldown())),
			new Workout(19L, "Schwelle 3 × 15", WorkoutType.THRESHOLD,
					"45 Minuten an der FTP – die Königsdisziplin des Schwellentrainings.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(15), 98), steady(RECOVERY, min(6), 55)),
							cooldown())),

			// ---------- Over-Unders ----------
			new Workout(20L, "Over-Unders 2 × 9", WorkoutType.OVER_UNDER,
					"Zwei Blöcke mit Wechseln knapp unter und über der Schwelle.",
					steps(warmup(),
							repeat(2, repeat(3, steady(INTERVAL, min(2), 95), steady(INTERVAL, min(1), 105)),
									steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(7L, "Over-Unders 3 × 12", WorkoutType.OVER_UNDER,
					"Wechsel knapp unter und über der Schwelle – trainiert das Abbauen von Laktat.",
					steps(warmup(),
							repeat(3, repeat(4, steady(INTERVAL, min(2), 95), steady(INTERVAL, min(1), 105)),
									steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(21L, "Over-Unders 3 × 15", WorkoutType.OVER_UNDER,
					"Lange Over-Under-Blöcke mit härteren Spitzen – wie Tempowechsel im Rennen.",
					steps(warmup(),
							repeat(3, repeat(5, steady(INTERVAL, min(2), 95), steady(INTERVAL, min(1), 108)),
									steady(RECOVERY, min(5), 55)),
							cooldown())),

			// ---------- VO2max ----------
			new Workout(22L, "VO2max 2 × 10 × 30/30", WorkoutType.VO2MAX,
					"30 Sekunden hart, 30 Sekunden locker – der sanfte Einstieg ins VO2max-Training.",
					steps(warmup(),
							repeat(2, repeat(10, steady(INTERVAL, 30, 120), steady(RECOVERY, 30, 50)),
									steady(RECOVERY, min(5), 50)),
							cooldown())),
			new Workout(23L, "VO2max 5 × 3", WorkoutType.VO2MAX,
					"Drei Minuten weit über der Schwelle, genauso lange Pause.",
					steps(warmup(), repeat(5, steady(INTERVAL, min(3), 115), steady(RECOVERY, min(3), 50)),
							cooldown())),
			new Workout(8L, "VO2max 5 × 4", WorkoutType.VO2MAX,
					"Kurze, sehr harte Intervalle für mehr Sauerstoffaufnahme.",
					steps(warmup(), repeat(5, steady(INTERVAL, min(4), 115), steady(RECOVERY, min(4), 50)),
							cooldown())),
			new Workout(24L, "VO2max 5 × 5", WorkoutType.VO2MAX,
					"Fünf Minuten am Anschlag, fünfmal – die längste und härteste VO2max-Variante.",
					steps(warmup(), repeat(5, steady(INTERVAL, min(5), 112), steady(RECOVERY, min(5), 50)),
							cooldown())),

			// ---------- Anaerob ----------
			new Workout(25L, "Anaerob 6 × 1", WorkoutType.ANAEROBIC,
					"Sechs einminütige Attacken weit über der Schwelle.",
					steps(warmup(), repeat(6, steady(INTERVAL, min(1), 130), steady(RECOVERY, min(2), 50)),
							cooldown())),
			new Workout(9L, "Anaerob 8 × 1", WorkoutType.ANAEROBIC,
					"Einminütige Attacken weit über der Schwelle – für Antritte und kurze Anstiege.",
					steps(warmup(), repeat(8, steady(INTERVAL, min(1), 130), steady(RECOVERY, min(2), 50)),
							cooldown())),
			new Workout(26L, "Anaerob 10 × 1", WorkoutType.ANAEROBIC,
					"Zehn Attacken mit verkürzten Pausen – trainiert die Erholung zwischen Antritten.",
					steps(warmup(), repeat(10, steady(INTERVAL, min(1), 130), steady(RECOVERY, 90, 50)),
							cooldown())),

			// ---------- Sprint & Test ----------
			new Workout(10L, "Sprints 6 × 15 s", WorkoutType.SPRINT,
					"Maximale Sprints, eingebettet in eine lockere Grundlage.",
					steps(warmup(), steady(STEADY, min(20), 65),
							repeat(6, steady(INTERVAL, 15, 170), steady(RECOVERY, min(4) + 45, 55)), cooldown())),

			new Workout(11L, "FTP-Test 20 min", WorkoutType.FTP_TEST,
					"20 Minuten so hart wie möglich, aber gleichmäßig. 95 % des Durchschnitts ergeben deine FTP.",
					steps(ramp(WARMUP, min(15), 50, 75), steady(INTERVAL, min(5), 110), steady(RECOVERY, min(10), 50),
							steady(INTERVAL, min(20), 100), ramp(COOLDOWN, min(15), 60, 45))));

	public List<Workout> findAll() {
		return workouts;
	}

	public Optional<Workout> findById(Long id) {
		return workouts.stream().filter(workout -> workout.getId().equals(id)).findFirst();
	}

	/** Alle Varianten einer Trainingsart, von leicht nach schwer */
	public List<Workout> variantsOf(WorkoutType type) {
		return workouts.stream().filter(workout -> workout.getType() == type).toList();
	}

	/** Variante einer Trainingsart nach Steigerungsstufe (0 = leichteste); zu hohe Stufen ergeben die schwerste. */
	public Workout variant(WorkoutType type, int step) {
		List<Workout> variants = variantsOf(type);
		return variants.get(Math.clamp(step, 0, variants.size() - 1));
	}

	/**
	 * Liefert ein Workout aus der Bibliothek. Für Regeneration, Grundlage und lange Ausfahrt kann eine
	 * abweichende Dauer angegeben werden, dann wird das Workout passend zur verfügbaren Zeit erzeugt.
	 */
	public Optional<Workout> resolve(Long id, Integer durationMinutes) {
		if (durationMinutes == null) {
			return findById(id);
		}
		return switch (id.intValue()) {
			case 1 -> Optional.of(recovery(durationMinutes));
			case 2 -> Optional.of(endurance(durationMinutes));
			case 3 -> Optional.of(longRide(durationMinutes));
			default -> findById(id);
		};
	}

	// ---------- Workouts mit variabler Dauer ----------

	public static Workout recovery(int minutes) {
		return new Workout(1L, "Lockeres Ausrollen " + formatMinutes(minutes), WorkoutType.RECOVERY,
				"Ganz locker treten, damit sich die Beine von harten Tagen erholen.",
				steps(steady(STEADY, min(minutes), 50)));
	}

	public static Workout endurance(int minutes) {
		return new Workout(2L, "Grundlage " + formatMinutes(minutes), WorkoutType.ENDURANCE,
				"Gleichmäßige Fahrt in Zone 2. Baut Ausdauer und Fettstoffwechsel auf.",
				steps(warmup(), steady(STEADY, min(minutes - 20), 68), cooldown()));
	}

	public static Workout longRide(int minutes) {
		return new Workout(3L, "Lange Ausfahrt " + formatMinutes(minutes), WorkoutType.LONG_RIDE,
				"Lange Grundlage – gewöhnt den Körper an die Belastung langer Event-Distanzen.",
				steps(ramp(WARMUP, min(15), 50, 68), steady(STEADY, min(minutes - 30), 68),
						ramp(COOLDOWN, min(15), 65, 50)));
	}

	/** 45 → "45 min", 180 → "3 h", 210 → "3:30 h" */
	static String formatMinutes(int minutes) {
		if (minutes < 120) {
			return minutes + " min";
		}
		int hours = minutes / 60;
		int rest = minutes % 60;
		return rest == 0 ? hours + " h" : "%d:%02d h".formatted(hours, rest);
	}

	// ---------- Hilfsmethoden zum Zusammenbauen der Workouts ----------

	private static int min(int minutes) {
		return minutes * 60;
	}

	private static WorkoutStep warmup() {
		return ramp(WARMUP, min(10), 50, 75);
	}

	private static WorkoutStep cooldown() {
		return ramp(COOLDOWN, min(10), 60, 45);
	}

	/** Fügt einzelne Abschnitte und bereits wiederholte Blöcke zu einer flachen Liste zusammen. */
	private static List<WorkoutStep> steps(Object... parts) {
		List<WorkoutStep> result = new ArrayList<>();
		for (Object part : parts) {
			if (part instanceof WorkoutStep step) {
				result.add(step);
			} else if (part instanceof List<?> block) {
				block.forEach(step -> result.add((WorkoutStep) step));
			}
		}
		return List.copyOf(result);
	}

	/** Wiederholt einen Block aus Abschnitten n-mal. */
	private static List<WorkoutStep> repeat(int times, Object... parts) {
		List<WorkoutStep> block = steps(parts);
		List<WorkoutStep> result = new ArrayList<>();
		for (int i = 0; i < times; i++) {
			result.addAll(block);
		}
		return result;
	}

}

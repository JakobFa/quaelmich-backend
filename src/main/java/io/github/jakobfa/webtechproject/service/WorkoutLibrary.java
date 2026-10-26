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

			longRideWithTempo(240),

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
			new Workout(28L, "Opener 35 min", WorkoutType.SPRINT,
					"Für den Tag vor dem Event: kurz locker, dazu ein paar Antritte, damit die Beine wach sind.",
					steps(warmup(), steady(STEADY, min(5), 65),
							repeat(3, steady(INTERVAL, min(1), 105), steady(RECOVERY, min(2), 55)),
							repeat(2, steady(INTERVAL, 10, 150), steady(RECOVERY, 50, 55)), cooldown())),

			new Workout(10L, "Sprints 6 × 15 s", WorkoutType.SPRINT,
					"Maximale Sprints, eingebettet in eine lockere Grundlage.",
					steps(warmup(), steady(STEADY, min(20), 65),
							repeat(6, steady(INTERVAL, 15, 170), steady(RECOVERY, min(4) + 45, 55)), cooldown())),

			new Workout(11L, "Rampentest", WorkoutType.FTP_TEST,
					"Jede Minute ein bisschen mehr, bis nichts mehr geht – dann aufhören. Deine FTP sind 75 % "
							+ "deiner besten Minute. Am besten im ERG-Modus auf der Rolle.",
					rampTest()));

	/**
	 * Abwechslung: weitere Workouts neben der Steigerung oben (variantsOf). Der Plan wechselt sie Woche für Woche
	 * mit der Variante derselben Stufe ab (siehe alternatives), dazu gibt es sie in Auswahl, Zufall und
	 * Bibliothek. IDs ab 50.
	 */
	private final List<Workout> variety = List.of(
			// ---------- Grundlage mit Einlagen (Länge frei wählbar) ----------
			enduranceWithSprints(75),
			enduranceWithTempo(75),

			// ---------- Tempo ----------
			new Workout(50L, "Tempo 4 × 10", WorkoutType.TEMPO,
					"Vier kürzere Tempo-Blöcke mit kurzer Pause – gleicher Reiz wie 2 × 20, fühlt sich leichter an.",
					steps(warmup(), repeat(4, steady(INTERVAL, min(10), 83), steady(RECOVERY, min(3), 55)),
							cooldown())),
			new Workout(51L, "Tempo mit Antritten 3 × 12", WorkoutType.TEMPO,
					"Tempo mit kurzen Antritten alle zwei Minuten – wie in der Gruppe, wenn vorne jemand anzieht.",
					steps(warmup(),
							repeat(3, repeat(6, steady(INTERVAL, 110, 84), steady(INTERVAL, 10, 140)),
									steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(66L, "Tempo 3 × 10", WorkoutType.TEMPO,
					"Drei kurze Tempo-Blöcke – der sanfte Einstieg, wenn 2 × 15 zu lang wirkt.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(10), 80), steady(RECOVERY, min(4), 55)),
							cooldown())),
			new Workout(52L, "Tempo-Pyramide 10-15-10", WorkoutType.TEMPO,
					"Kurz, lang, kurz – der lange Block in der Mitte etwas härter.",
					steps(warmup(), steady(INTERVAL, min(10), 82), steady(RECOVERY, min(5), 55),
							steady(INTERVAL, min(15), 86), steady(RECOVERY, min(5), 55), steady(INTERVAL, min(10), 82),
							steady(RECOVERY, min(5), 55), cooldown())),

			// ---------- Sweet Spot ----------
			new Workout(53L, "Sweet Spot 2 × 12", WorkoutType.SWEET_SPOT,
					"Unter einer Stunde: zwei Sweet-Spot-Blöcke für Tage mit wenig Zeit.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(12), 90), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(54L, "Sweet Spot 4 × 8", WorkoutType.SWEET_SPOT,
					"Vier knackige Blöcke am oberen Sweet Spot mit kurzen Pausen.",
					steps(warmup(), repeat(4, steady(INTERVAL, min(8), 92), steady(RECOVERY, min(4), 55)),
							cooldown())),
			new Workout(67L, "Sweet Spot 5 × 8", WorkoutType.SWEET_SPOT,
					"Fünf Blöcke mit kurzen Pausen – 40 Minuten Sweet Spot, gut verdaulich portioniert.",
					steps(warmup(), repeat(5, steady(INTERVAL, min(8), 90), steady(RECOVERY, min(3), 55)),
							cooldown())),
			new Workout(55L, "Sweet-Spot-Treppe 8-10-12", WorkoutType.SWEET_SPOT,
					"Jeder Block etwas länger und etwas härter – von 88 bis 92 % FTP.",
					steps(warmup(), steady(INTERVAL, min(8), 88), steady(RECOVERY, min(4), 55),
							steady(INTERVAL, min(10), 90), steady(RECOVERY, min(4), 55), steady(INTERVAL, min(12), 92),
							steady(RECOVERY, min(5), 55), cooldown())),

			// ---------- Schwelle ----------
			new Workout(56L, "Schwelle 2 × 10", WorkoutType.THRESHOLD,
					"Zweimal zehn Minuten an der FTP – Schwellentraining in unter einer Stunde.",
					steps(warmup(), repeat(2, steady(INTERVAL, min(10), 98), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(57L, "Schwelle 4 × 8", WorkoutType.THRESHOLD,
					"Vier Blöcke genau an der FTP mit kurzen Pausen – viel Zeit an der Schwelle.",
					steps(warmup(), repeat(4, steady(INTERVAL, min(8), 100), steady(RECOVERY, min(4), 55)),
							cooldown())),
			new Workout(68L, "Schwelle 3 × 12", WorkoutType.THRESHOLD,
					"Drei Blöcke an der FTP – zwischen 3 × 10 und 3 × 15.",
					steps(warmup(), repeat(3, steady(INTERVAL, min(12), 98), steady(RECOVERY, min(5), 55)),
							cooldown())),
			new Workout(58L, "Schwelle 1 × 20", WorkoutType.THRESHOLD,
					"Ein einziger Block an der Schwelle – übt das gleichmäßige Durchziehen wie im Zeitfahren.",
					steps(ramp(WARMUP, min(15), 50, 80), steady(INTERVAL, min(20), 96), steady(RECOVERY, min(5), 55),
							cooldown())),

			// ---------- Over-Unders ----------
			new Workout(59L, "Over-Unders 2 × 12 (1/1)", WorkoutType.OVER_UNDER,
					"Jede Minute ein Wechsel zwischen knapp unter und deutlich über der Schwelle.",
					steps(warmup(),
							repeat(2, repeat(6, steady(INTERVAL, min(1), 92), steady(INTERVAL, min(1), 108)),
									steady(RECOVERY, min(6), 55)),
							cooldown())),

			new Workout(69L, "Over-Unders 4 × 9", WorkoutType.OVER_UNDER,
					"Vier kürzere Blöcke mit 2 min unter und 1 min über der Schwelle.",
					steps(warmup(),
							repeat(4, repeat(3, steady(INTERVAL, min(2), 95), steady(INTERVAL, min(1), 105)),
									steady(RECOVERY, min(4), 55)),
							cooldown())),
			new Workout(70L, "Over-Unders 3 × 16 (3/1)", WorkoutType.OVER_UNDER,
					"Lange Blöcke: drei Minuten knapp unter, eine deutlich über der Schwelle.",
					steps(warmup(),
							repeat(3, repeat(4, steady(INTERVAL, min(3), 95), steady(INTERVAL, min(1), 107)),
									steady(RECOVERY, min(5), 55)),
							cooldown())),

			// ---------- VO2max ----------
			new Workout(60L, "VO2max 4 × 4", WorkoutType.VO2MAX,
					"Vier Mal vier Minuten weit über der Schwelle – der Klassiker in kurz.",
					steps(warmup(), repeat(4, steady(INTERVAL, min(4), 115), steady(RECOVERY, min(4), 50)),
							cooldown())),
			new Workout(61L, "VO2max 6 × 2", WorkoutType.VO2MAX,
					"Kurze, sehr harte Zwei-Minuten-Intervalle – schnell am Limit, schnell wieder vorbei.",
					steps(warmup(), repeat(6, steady(INTERVAL, min(2), 120), steady(RECOVERY, min(2), 50)),
							cooldown())),
			new Workout(62L, "VO2max 3 × 13 × 30/15", WorkoutType.VO2MAX,
					"30 Sekunden hart, 15 locker, 13 Mal am Stück: der Puls bleibt oben, die Beine kommen kurz zur Ruhe.",
					steps(warmup(),
							repeat(3, repeat(13, steady(INTERVAL, 30, 118), steady(RECOVERY, 15, 50)),
									steady(RECOVERY, min(3), 50)),
							cooldown())),
			new Workout(63L, "VO2max 3 × 8 × 40/20", WorkoutType.VO2MAX,
					"40 Sekunden hart, 20 locker – längere Spitzen als bei 30/30, kürzere Pausen.",
					steps(warmup(),
							repeat(3, repeat(8, steady(INTERVAL, 40, 120), steady(RECOVERY, 20, 50)),
									steady(RECOVERY, min(5), 50)),
							cooldown())),

			// ---------- Anaerob ----------
			new Workout(64L, "Anaerob 2 × 5 × 30 s", WorkoutType.ANAEROBIC,
					"Zwei Serien kurzer, sehr harter Antritte – für Rampen und Attacken.",
					steps(warmup(),
							repeat(2, repeat(5, steady(INTERVAL, 30, 150), steady(RECOVERY, 90, 50)),
									steady(RECOVERY, min(5), 50)),
							cooldown())),

			// ---------- Sprint ----------
			new Workout(65L, "Sprints 8 × 10 s", WorkoutType.SPRINT,
					"Acht kurze Vollgas-Sprints in lockerer Grundlage – viel Spritzigkeit, wenig Ermüdung.",
					steps(warmup(), steady(STEADY, min(15), 65),
							repeat(8, steady(INTERVAL, 10, 180), steady(RECOVERY, min(3) + 50, 55)), cooldown())));

	/** Alle Radeinheiten: Steigerung plus Abwechslung */
	private final List<Workout> allRides = java.util.stream.Stream.concat(workouts.stream(), variety.stream())
			.toList();

	/** Rampentest: ab RAMP_START_PERCENT jede Minute RAMP_STEP_PERCENT mehr bis RAMP_END_PERCENT (da ist jeder fertig) */
	static final int RAMP_STEP_PERCENT = 6;
	static final int RAMP_START_PERCENT = 60;
	static final int RAMP_END_PERCENT = 162;

	private static List<WorkoutStep> rampTest() {
		List<Object> parts = new ArrayList<>();
		parts.add(ramp(WARMUP, min(8), 45, 60));
		for (int percent = RAMP_START_PERCENT; percent <= RAMP_END_PERCENT; percent += RAMP_STEP_PERCENT) {
			parts.add(steady(INTERVAL, min(1), percent));
		}
		parts.add(ramp(COOLDOWN, min(10), 55, 40));
		return steps(parts.toArray());
	}

	/** Workout-ID des FTP-Tests */
	public static final long FTP_TEST = 11L;

	/** Feste Workout-IDs der Krafteinheiten (die Übungen dazu kennt die App) */
	public static final long STRENGTH_BASE_A = 40L;
	public static final long STRENGTH_BASE_B = 41L;
	public static final long STRENGTH_MAINTENANCE = 42L;

	/**
	 * Krafteinheiten für zu Hause ohne Geräte, gemischt mit Mobility (vorher lockern, nachher dehnen).
	 * Nicht in {@link #findAll()} – man kann sie nicht statt einer Radeinheit wählen.
	 * Die Last ist grob geschätzt (ein gleichmäßiger Abschnitt bei 55 %), damit sie in Wochen-TSS und
	 * Belastungs-Check mitzählt.
	 */
	private final List<Workout> strengthWorkouts = List.of(
			new Workout(STRENGTH_BASE_A, "Kraft & Mobility A: Grundkraft", WorkoutType.STRENGTH,
					"Zu Hause, ohne Geräte: Katze-Kuh, dann Kniebeuge, einbeiniges Kreuzheben, Hüftheben, "
							+ "Unterarmstütz – zum Schluss die Hüftbeuger dehnen.",
					List.of(WorkoutStep.steady(STEADY, min(30), 55))),
			new Workout(STRENGTH_BASE_B, "Kraft & Mobility B: Beine einzeln und Rumpf", WorkoutType.STRENGTH,
					"Zu Hause, ohne Geräte: Kindhaltung, dann Ausfallschritt, Wadenheben, Dead Bug, Seitstütz – "
							+ "zum Schluss die Beinrückseite dehnen.",
					List.of(WorkoutStep.steady(STEADY, min(30), 55))),
			new Workout(STRENGTH_MAINTENANCE, "Kraft & Mobility: Erhalten", WorkoutType.STRENGTH,
					"Kurz und ohne Geräte: weniger Sätze, dafür langsam – hält die Kraft, ohne die Beine fürs Rad "
							+ "zu ermüden.",
					List.of(WorkoutStep.steady(STEADY, min(20), 55))));

	/** IDs ab hier sind selbst gebaute Workouts (siehe CustomWorkout) – nie Teil der öffentlichen Bibliothek */
	public static final long CUSTOM_ID_OFFSET = 1_000_000L;

	private java.util.function.Function<Long, Optional<Workout>> customWorkouts = id -> Optional.empty();

	/** Damit eingeplante eigene Workouts aufgelöst werden können (setzt CustomWorkoutService) */
	public void setCustomWorkouts(java.util.function.Function<Long, Optional<Workout>> lookup) {
		this.customWorkouts = lookup;
	}

	public List<Workout> findAll() {
		return allRides;
	}

	public Optional<Workout> findById(Long id) {
		return java.util.stream.Stream.concat(allRides.stream(), strengthWorkouts.stream())
				.filter(workout -> workout.getId().equals(id)).findFirst();
	}

	public static boolean isStrength(Workout workout) {
		return workout.getType() == WorkoutType.STRENGTH;
	}

	/** Ohne Nachschlagen: ist das eine der Krafteinheiten? */
	public static boolean isStrengthId(Long workoutId) {
		return workoutId != null && workoutId >= STRENGTH_BASE_A && workoutId <= STRENGTH_MAINTENANCE;
	}

	/** Alle Varianten einer Trainingsart, von leicht nach schwer – nur die Steigerung, ohne Abwechslung */
	public List<Workout> variantsOf(WorkoutType type) {
		return workouts.stream().filter(workout -> workout.getType() == type).toList();
	}

	/** Die nächstleichtere Variante derselben Trainingsart, leer wenn es schon die leichteste ist */
	public Optional<Workout> easierVariant(Workout workout) {
		List<Workout> variants = variantsOf(workout.getType());
		Workout original = findById(workout.getId()).orElse(workout);
		int index = variants.indexOf(original);
		if (index < 0) {
			// Abwechslung: die härteste Stufe, die leichter ist
			return variants.stream().filter(v -> v.getTss() < original.getTss()).reduce((a, b) -> b);
		}
		return index > 0 ? Optional.of(variants.get(index - 1)) : Optional.empty();
	}

	/** Die nächstschwerere Variante derselben Trainingsart, leer wenn es schon die schwerste ist */
	public Optional<Workout> harderVariant(Workout workout) {
		List<Workout> variants = variantsOf(workout.getType());
		Workout original = findById(workout.getId()).orElse(workout);
		int index = variants.indexOf(original);
		if (index < 0) {
			// Abwechslung: die leichteste Stufe, die härter ist
			return variants.stream().filter(v -> v.getTss() > original.getTss()).findFirst();
		}
		return index < variants.size() - 1 ? Optional.of(variants.get(index + 1)) : Optional.empty();
	}

	/** Variante einer Trainingsart nach Steigerungsstufe (0 = leichteste); zu hohe Stufen ergeben die schwerste. */
	public Workout variant(WorkoutType type, int step) {
		List<Workout> variants = variantsOf(type);
		return variants.get(Math.clamp(step, 0, variants.size() - 1));
	}

	/** Der Opener ist fürs Vortag-Event gedacht und bleibt kurz */
	static final long OPENER = 28L;

	/**
	 * Was der Plan auf dieser Stufe fahren kann: die Variante der Steigerung und alle Abwechslungs-Workouts
	 * derselben Art, deren Belastung (TSS) dieser Stufe am nächsten liegt. Die Variante steht vorn.
	 */
	public List<Workout> alternatives(WorkoutType type, int step) {
		List<Workout> variants = variantsOf(type);
		if (variants.isEmpty()) {
			return List.of();
		}
		int index = Math.clamp(step, 0, variants.size() - 1);
		List<Workout> result = new ArrayList<>();
		result.add(variants.get(index));
		variety.stream()
				.filter(workout -> workout.getType() == type && nearestStep(variants, workout) == index)
				.forEach(result::add);
		return result;
	}

	private static int nearestStep(List<Workout> variants, Workout workout) {
		int best = 0;
		for (int i = 1; i < variants.size(); i++) {
			if (Math.abs(variants.get(i).getTss() - workout.getTss())
					< Math.abs(variants.get(best).getTss() - workout.getTss())) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * Lässt sich die Dauer dieses festen Workouts über den Rahmen (Aufwärmen, Grundlage, Ausfahren) anpassen?
	 * Nicht beim FTP-Test, beim Opener, bei Kraft und eigenen Workouts.
	 */
	public static boolean isLengthAdjustable(Workout workout) {
		long id = workout.getId();
		return id < CUSTOM_ID_OFFSET && id != OPENER && workout.getType() != WorkoutType.FTP_TEST
				&& !isStrength(workout) && !WorkoutCustomizationService.isVariable(id)
				&& WorkoutFitter.isFittable(workout);
	}

	/**
	 * Liefert ein Workout aus der Bibliothek. Für Regeneration, Grundlage und lange Ausfahrt kann eine
	 * abweichende Dauer angegeben werden, dann wird das Workout passend zur verfügbaren Zeit erzeugt.
	 * Intervall-Workouts mit Dauer bekommen einen angepassten Rahmen (siehe WorkoutFitter).
	 */
	public Optional<Workout> resolve(Long id, Integer durationMinutes) {
		if (id != null && id >= CUSTOM_ID_OFFSET) {
			return customWorkouts.apply(id);
		}
		if (durationMinutes == null) {
			return findById(id);
		}
		return switch (id.intValue()) {
			case 1 -> Optional.of(recovery(durationMinutes));
			case 2 -> Optional.of(endurance(durationMinutes));
			case 3 -> Optional.of(longRide(durationMinutes));
			case 27 -> Optional.of(longRideWithTempo(durationMinutes));
			case 29 -> Optional.of(enduranceWithSprints(durationMinutes));
			case 30 -> Optional.of(enduranceWithTempo(durationMinutes));
			default -> findById(id).map(workout -> isLengthAdjustable(workout)
					? WorkoutFitter.fit(workout, durationMinutes).orElse(workout)
					: workout);
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

	/**
	 * Lange Ausfahrt mit Tempo-Blöcken im "Renntempo" (85 % FTP) – für lange Events im Aufbau:
	 * 2 Blöcke à 20 min, ab 3:30 h drei. Dazwischen Grundlage.
	 */
	public static Workout longRideWithTempo(int minutes) {
		int blocks = minutes >= 210 ? 3 : 2;
		int easy = minutes - 30 - blocks * 20;
		int between = easy / (blocks + 1);
		List<Object> parts = new ArrayList<>();
		parts.add(ramp(WARMUP, min(15), 50, 68));
		for (int i = 0; i < blocks; i++) {
			parts.add(steady(STEADY, min(between), 68));
			parts.add(steady(INTERVAL, min(20), 85));
		}
		parts.add(steady(STEADY, min(easy - between * blocks), 68));
		parts.add(ramp(COOLDOWN, min(15), 65, 50));
		return new Workout(27L, "Lange Ausfahrt " + formatMinutes(minutes) + " mit Tempo", WorkoutType.LONG_RIDE,
				"Lange Grundlage mit %d × 20 min im Renntempo – übt das gleichmäßige Fahren über Stunden."
						.formatted(blocks),
				steps(parts.toArray()));
	}

	/**
	 * Grundlage mit ein paar kurzen Antritten (10 s, gleichmäßig verteilt) – bringt Spritzigkeit, kaum Ermüdung.
	 * Ab 60 min; je angefangene 10 min Grundlage ein Antritt, höchstens 8.
	 */
	public static Workout enduranceWithSprints(int minutes) {
		int easy = minutes - 20;
		int sprints = Math.clamp(easy / 10, 3, 8);
		int between = (easy * 60 - sprints * 10) / (sprints + 1);
		List<Object> parts = new ArrayList<>();
		parts.add(warmup());
		for (int i = 0; i < sprints; i++) {
			parts.add(steady(STEADY, between, 68));
			parts.add(steady(INTERVAL, 10, 150));
		}
		parts.add(steady(STEADY, easy * 60 - sprints * (between + 10), 68));
		parts.add(cooldown());
		return new Workout(29L, "Grundlage %s mit Antritten".formatted(formatMinutes(minutes)), WorkoutType.ENDURANCE,
				"Locker in Zone 2, dazu %d kurze Antritte à 10 Sekunden – kräftig, aber nicht all-out.".formatted(sprints),
				steps(parts.toArray()));
	}

	/** Grundlage mit drei zügigen Blöcken (6 min bei 80 %) in der Mitte – ab 60 min */
	public static Workout enduranceWithTempo(int minutes) {
		int easy = minutes - 20 - 3 * 6;
		int between = easy / 4;
		List<Object> parts = new ArrayList<>();
		parts.add(warmup());
		for (int i = 0; i < 3; i++) {
			parts.add(steady(STEADY, min(between), 68));
			parts.add(steady(INTERVAL, min(6), 80));
		}
		parts.add(steady(STEADY, min(easy - 3 * between), 68));
		parts.add(cooldown());
		return new Workout(30L, "Grundlage %s mit Tempo-Einlagen".formatted(formatMinutes(minutes)),
				WorkoutType.ENDURANCE, "Locker in Zone 2, dazwischen dreimal 6 Minuten zügig – noch kein hartes Training.",
				steps(parts.toArray()));
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

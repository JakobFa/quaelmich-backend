package io.github.jakobfa.webtechproject.service;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/**
 * Passt ein geplantes Workout auf Wunsch an: leichter, härter, kürzer, länger – oder zurück zum Original.
 *
 * - Intervall-Workouts wechseln zur nächstleichteren bzw. -schwereren oder -kürzeren bzw. -längeren Variante.
 * - Lockere Fahrten (Ausrollen, Grundlage, lange Ausfahrt) werden 15 bzw. 30 min kürzer oder länger;
 *   "härter" macht daraus die nächste Stufe (Ausrollen → Grundlage → mit Tempo-Blöcken).
 * - Erledigte Workouts bleiben, wie sie sind.
 */
@Service
public class WorkoutCustomizationService {

	public enum Direction {
		EASIER, HARDER, SHORTER, LONGER
	}

	/** Welche Knöpfe gerade möglich sind */
	public record Options(boolean easier, boolean harder, boolean shorter, boolean longer, boolean reset) {
	}

	/** Wird geworfen, wenn die gewünschte Änderung nicht geht (z. B. leichter als Ausrollen) */
	public static class NotPossibleException extends RuntimeException {
	}

	/** Ein Workout aus der Bibliothek, bei variabler Dauer mit Minuten */
	record Choice(long workoutId, Integer durationMinutes) {

		static Choice of(Workout workout) {
			return new Choice(workout.getId(), null);
		}
	}

	static final long RECOVERY = 1;
	static final long ENDURANCE = 2;
	static final long LONG_RIDE = 3;
	static final long LONG_RIDE_WITH_TEMPO = 27;
	static final long ENDURANCE_WITH_SPRINTS = 29;
	static final long ENDURANCE_WITH_TEMPO = 30;
	static final int MAX_MINUTES = 360;

	private final WorkoutLibrary library;
	private final PlannedWorkoutService plannedWorkoutService;
	private final PlannedWorkoutRepository repository;

	/** prüft, ob ein eigenes Workout dem Nutzer gehört (eigenes Interface, damit Tests ohne Datenbank laufen) */
	public interface CustomWorkoutOwnership {
		boolean isOwnedBy(Long workoutId, Long userId);
	}

	private final CustomWorkoutOwnership customWorkouts;

	public WorkoutCustomizationService(WorkoutLibrary library, PlannedWorkoutService plannedWorkoutService,
			PlannedWorkoutRepository repository, CustomWorkoutOwnership customWorkouts) {
		this.customWorkouts = customWorkouts;
		this.library = library;
		this.plannedWorkoutService = plannedWorkoutService;
		this.repository = repository;
	}

	public Options optionsFor(PlannedWorkout planned) {
		if (planned.isCompleted() || planned.getSkipReason() != null) {
			return new Options(false, false, false, false, false);
		}
		Workout current = workoutOf(planned);
		return new Options(
				choiceFor(current, Direction.EASIER).isPresent(),
				choiceFor(current, Direction.HARDER).isPresent(),
				choiceFor(current, Direction.SHORTER).isPresent(),
				choiceFor(current, Direction.LONGER).isPresent(),
				planned.getOriginalWorkoutId() != null);
	}

	/** Leer, wenn das Workout nicht existiert oder fremd ist */
	@Transactional
	public Optional<PlannedWorkout> adjust(Long id, Long userId, Direction direction) {
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			if (planned.isCompleted() || planned.getSkipReason() != null) {
				throw new NotPossibleException();
			}
			Workout current = workoutOf(planned);
			Choice choice = choiceFor(current, direction).orElseThrow(NotPossibleException::new);
			return apply(planned, current, choice,
					PlannedWorkout.USER_CHOICE_NOTE + " angepasst: " + label(direction) + ".");
		});
	}

	/**
	 * Heute etwas ganz anderes fahren: ein beliebiges Workout aus der Bibliothek statt des geplanten.
	 * Bei Workouts mit variabler Dauer (Ausrollen, Grundlage, lange Ausfahrt) kann die Dauer mitgegeben werden,
	 * sonst gilt die Standarddauer.
	 */
	@Transactional
	public Optional<PlannedWorkout> replace(Long id, Long userId, Long workoutId, Integer durationMinutes) {
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			if (planned.isCompleted() || planned.getSkipReason() != null) {
				throw new NotPossibleException();
			}
			// eigene Workouts nur vom Besitzer – die Bibliothek für alle
			Workout chosen = (customWorkouts.isOwnedBy(workoutId, userId)
					? library.resolve(workoutId, null)
					: library.findById(workoutId)).orElseThrow(NotPossibleException::new);
			Integer minutes = null;
			if (isVariable(workoutId)) {
				minutes = durationMinutes != null ? durationMinutes : chosen.getDurationInMinutes();
				if (minutes < minimumMinutes(workoutId) || minutes > MAX_MINUTES || minutes % 15 != 0) {
					throw new NotPossibleException();
				}
			}
			return apply(planned, workoutOf(planned), new Choice(workoutId, minutes),
					PlannedWorkout.USER_CHOICE_NOTE + " getauscht.");
		});
	}

	/**
	 * Wie hart sich ein Tag anfühlt – der Zufall bleibt auf derselben Stufe, damit aus einem Tempo-Tag in
	 * der Grundlagenphase kein VO2max-Tag wird. 0 = locker, 1 = Tempo/Sweet Spot, 2 = Schwelle, 3 = sehr hart.
	 */
	static int intensityLevel(WorkoutType type) {
		return switch (type) {
			case RECOVERY, ENDURANCE, LONG_RIDE -> 0;
			case TEMPO, SWEET_SPOT -> 1;
			case THRESHOLD, OVER_UNDER, FTP_TEST -> 2;
			case VO2MAX, ANAEROBIC, SPRINT -> 3;
		};
	}
	/** Ausrollen länger als eine Stunde ist kein sinnvolles Training mehr */
	static final int MAX_RECOVERY_MINUTES = 60;
	/** Feste Workouts sollen die freie Zeit wenigstens zu gut der Hälfte nutzen */
	static final double MIN_SHARE_OF_AVAILABLE_TIME = 0.6;
	/** so viel länger als die verfügbare Zeit darf der Zufall werden, wenn sonst nichts passt */
	static final double RANDOM_OVERTIME_SHARE = 0.1;

	private final RandomGenerator random = new Random();

	/**
	 * "Überrasch mich": ein zufälliges anderes Workout, das in die verfügbare Zeit des Tages passt
	 * (Wochen-Ausnahme, sonst normale Verfügbarkeit). Es bleibt bei derselben Härte-Stufe (siehe
	 * intensityLevel) – so bringt der Zufall die Woche nicht durcheinander. Kein FTP-Test, kein Krafttraining,
	 * nie dasselbe noch mal.
	 */
	@Transactional
	public Optional<PlannedWorkout> shuffle(Long id, Long userId) {
		return shuffle(id, userId, random);
	}

	Optional<PlannedWorkout> shuffle(Long id, Long userId, RandomGenerator random) {
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			if (planned.isCompleted() || planned.getSkipReason() != null) {
				throw new NotPossibleException();
			}
			Workout current = workoutOf(planned);
			int minutes = availableMinutes(planned);
			List<Choice> candidates = randomCandidates(current, minutes > 0 ? minutes : current.getDurationInMinutes());
			if (candidates.isEmpty()) {
				throw new NotPossibleException();
			}
			Choice choice = candidates.get(random.nextInt(candidates.size()));
			return apply(planned, current, choice, PlannedWorkout.USER_CHOICE_NOTE + " per Zufall gewählt.");
		});
	}

	/**
	 * Alles, was der Zufall statt current nehmen darf, wenn minutes Zeit ist. Passt streng nichts (z. B. nur
	 * eine Stunde Zeit), darf es bis zu 10 % länger sein – sonst gäbe es an Wochentagen oft gar keine Auswahl.
	 */
	List<Choice> randomCandidates(Workout current, int minutes) {
		List<Choice> candidates = candidatesUpTo(current, minutes, minutes);
		return candidates.isEmpty()
				? candidatesUpTo(current, minutes, (int) Math.floor(minutes * (1 + RANDOM_OVERTIME_SHARE)))
				: candidates;
	}

	private List<Choice> candidatesUpTo(Workout current, int minutes, int maxMinutes) {
		int level = intensityLevel(current.getType());
		int quarterHours = Math.min(MAX_MINUTES, (int) (Math.floor(minutes / 15.0) * 15));
		List<Choice> candidates = new ArrayList<>();
		for (Workout workout : library.findAll()) {
			long workoutId = workout.getId();
			if (workout.getType() == WorkoutType.FTP_TEST || intensityLevel(workout.getType()) != level) {
				continue;
			}
			Choice choice;
			if (isVariable(workoutId)) {
				int length = workoutId == RECOVERY ? Math.min(quarterHours, MAX_RECOVERY_MINUTES) : quarterHours;
				if (length < minimumMinutes(workoutId)) {
					continue;
				}
				choice = new Choice(workoutId, length);
			} else if (WorkoutLibrary.isLengthAdjustable(workout)) {
				// Hauptteil bleibt, der Rahmen füllt die Zeit des Tages
				int length = Math.min(MAX_MINUTES, minutes - minutes % 5);
				if (WorkoutFitter.fit(workout, length).isEmpty()) {
					continue;
				}
				choice = lengthChoice(workout, length);
			} else {
				int duration = workout.getDurationInMinutes();
				if (duration > maxMinutes || duration < minutes * MIN_SHARE_OF_AVAILABLE_TIME) {
					continue;
				}
				choice = Choice.of(workout);
			}
			boolean same = workoutId == current.getId()
					&& (!isVariable(workoutId) || choice.durationMinutes() == current.getDurationInMinutes());
			if (!same) {
				candidates.add(choice);
			}
		}
		return candidates;
	}

	/** Verfügbare Minuten an diesem Tag: Wochen-Ausnahme vor normaler Verfügbarkeit */
	private static int availableMinutes(PlannedWorkout planned) {
		TrainingPlan plan = planned.getPlan();
		WeeklyAvailability availability = plan.getWeekAvailability().getOrDefault(
				planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
				plan.getAthlete().getAvailability());
		return availability == null ? 0
				: (int) Math.round(availability.hoursOn(planned.getDate().getDayOfWeek()) * 60);
	}

	private PlannedWorkout apply(PlannedWorkout planned, Workout current, Choice choice, String note) {
		if (planned.getOriginalWorkoutId() == null) {
			planned.setOriginalWorkoutId(planned.getWorkoutId());
			planned.setOriginalDurationMinutes(planned.getDurationMinutes());
		}
		if (planned.getOriginalTitle() == null) {
			planned.setOriginalTitle(current.getTitle());
		}
		planned.setWorkoutId(choice.workoutId());
		planned.setDurationMinutes(choice.durationMinutes());

		// wieder beim Original gelandet → als nicht angepasst behandeln
		if (choice.workoutId() == planned.getOriginalWorkoutId()
				&& java.util.Objects.equals(choice.durationMinutes(), planned.getOriginalDurationMinutes())) {
			clearAdjustment(planned);
		} else {
			planned.setAdjustmentNote(note);
		}
		return repository.save(planned);
	}

	/** Zurück zum ursprünglich geplanten Workout */
	@Transactional
	public Optional<PlannedWorkout> reset(Long id, Long userId) {
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			if (planned.isCompleted() || planned.getOriginalWorkoutId() == null) {
				throw new NotPossibleException();
			}
			planned.setWorkoutId(planned.getOriginalWorkoutId());
			planned.setDurationMinutes(planned.getOriginalDurationMinutes());
			clearAdjustment(planned);
			return repository.save(planned);
		});
	}

	private static void clearAdjustment(PlannedWorkout planned) {
		planned.setOriginalWorkoutId(null);
		planned.setOriginalDurationMinutes(null);
		planned.setOriginalTitle(null);
		planned.setAdjustmentNote(null);
	}

	private static String label(Direction direction) {
		return switch (direction) {
			case EASIER -> "leichter";
			case HARDER -> "härter";
			case SHORTER -> "kürzer";
			case LONGER -> "länger";
		};
	}

	// ---------- Regeln ----------

	Optional<Choice> choiceFor(Workout current, Direction direction) {
		long id = current.getId();
		if (id >= WorkoutLibrary.CUSTOM_ID_OFFSET) {
			return Optional.empty(); // eigene Workouts haben keine leichteren/härteren Stufen
		}
		int minutes = current.getDurationInMinutes();
		boolean variable = isVariable(id);
		if (current.getType() == WorkoutType.FTP_TEST) {
			return Optional.empty(); // ein Test bleibt ein Test
		}
		if (variable) {
			return variableChoice(id, minutes, direction);
		}
		return switch (direction) {
			case EASIER -> library.easierVariant(current).map(variant -> keepingLength(current, variant))
					.or(() -> Optional.of(new Choice(ENDURANCE, Math.max(60, roundToQuarterHour(minutes)))));
			case HARDER -> library.harderVariant(current).map(variant -> keepingLength(current, variant));
			// erst den Rahmen ändern, der Hauptteil bleibt; geht das nicht mehr, die kürzere Variante
			case SHORTER -> withLength(current, minutes - LENGTH_STEP)
					.or(() -> neighbourByDuration(current, false).map(Choice::of));
			case LONGER -> withLength(current, minutes + LENGTH_STEP)
					.or(() -> neighbourByDuration(current, true).map(Choice::of));
		};
	}

	/** So viele Minuten machen "kürzer" und "länger" bei Intervall-Workouts */
	static final int LENGTH_STEP = 15;

	/** Dasselbe Workout mit anderem Rahmen; leer, wenn das nicht geht */
	private Optional<Choice> withLength(Workout current, int minutes) {
		if (!WorkoutLibrary.isLengthAdjustable(current) || minutes > MAX_MINUTES) {
			return Optional.empty();
		}
		Workout original = library.findById(current.getId()).orElse(current);
		return WorkoutFitter.fit(original, minutes).map(fitted -> lengthChoice(original, minutes));
	}

	/** Bei leichter/härter bleibt eine schon angepasste Länge erhalten, wenn sie für die Variante geht */
	private Choice keepingLength(Workout current, Workout variant) {
		Workout original = library.findById(current.getId()).orElse(current);
		int minutes = current.getDurationInMinutes();
		if (minutes != original.getDurationInMinutes() && WorkoutLibrary.isLengthAdjustable(variant)
				&& WorkoutFitter.fit(variant, minutes).isPresent()) {
			return lengthChoice(variant, minutes);
		}
		return Choice.of(variant);
	}

	/** Standardlänge wird als "keine eigene Dauer" gespeichert */
	private static Choice lengthChoice(Workout original, int minutes) {
		return new Choice(original.getId(), minutes == original.getDurationInMinutes() ? null : minutes);
	}

	private static Optional<Choice> variableChoice(long id, int minutes, Direction direction) {
		int step = minutes >= 120 ? 30 : 15;
		return switch (direction) {
			case EASIER -> {
				if (id == RECOVERY) {
					yield Optional.empty();
				}
				if (id == ENDURANCE) {
					yield Optional.of(new Choice(RECOVERY, Math.min(minutes, 60)));
				}
				if (id == ENDURANCE_WITH_SPRINTS || id == ENDURANCE_WITH_TEMPO) {
					yield Optional.of(new Choice(ENDURANCE, minutes)); // Einlagen weglassen
				}
				if (id == LONG_RIDE) {
					yield Optional.of(new Choice(ENDURANCE, Math.max(60, roundToQuarterHour(minutes * 2 / 3.0))));
				}
				yield Optional.of(new Choice(LONG_RIDE, minutes)); // Tempo-Blöcke weglassen
			}
			case HARDER -> {
				if (id == RECOVERY) {
					yield Optional.of(new Choice(ENDURANCE, Math.max(60, minutes)));
				}
				if (id == ENDURANCE_WITH_SPRINTS) {
					yield Optional.of(new Choice(ENDURANCE_WITH_TEMPO, minutes));
				}
				if (id == ENDURANCE || id == LONG_RIDE || id == ENDURANCE_WITH_TEMPO) {
					yield Optional.of(new Choice(LONG_RIDE_WITH_TEMPO, Math.max(90, minutes)));
				}
				yield Optional.empty();
			}
			case SHORTER -> {
				int shorter = minutes - step;
				yield shorter >= minimumMinutes(id) ? Optional.of(new Choice(id, shorter)) : Optional.empty();
			}
			case LONGER -> {
				int longer = minutes + step;
				yield longer <= MAX_MINUTES ? Optional.of(new Choice(id, longer)) : Optional.empty();
			}
		};
	}

	/** Ausrollen, Grundlage und lange Ausfahrten gibt es in jeder Länge */
	static boolean isVariable(long id) {
		return id == RECOVERY || id == ENDURANCE || id == LONG_RIDE || id == LONG_RIDE_WITH_TEMPO
				|| id == ENDURANCE_WITH_SPRINTS || id == ENDURANCE_WITH_TEMPO;
	}

	static int minimumMinutes(long id) {
		if (id == RECOVERY) {
			return 30;
		}
		return id == ENDURANCE || id == ENDURANCE_WITH_SPRINTS || id == ENDURANCE_WITH_TEMPO ? 60 : 90;
	}


	/**
	 * Die Variante derselben Art, die als nächstes kürzer bzw. länger ist.
	 * "Kürzer" wird dabei nie härter, "länger" nie leichter (gemessen am TSS).
	 */
	private Optional<Workout> neighbourByDuration(Workout workout, boolean longer) {
		int minutes = workout.getDurationInMinutes();
		int tss = workout.getTss();
		var candidates = library.variantsOf(workout.getType()).stream()
				.filter(variant -> longer
						? variant.getDurationInMinutes() > minutes && variant.getTss() >= tss
						: variant.getDurationInMinutes() < minutes && variant.getTss() <= tss);
		Comparator<Workout> byDuration = Comparator.comparingInt(Workout::getDurationInMinutes);
		return longer ? candidates.min(byDuration) : candidates.max(byDuration);
	}

	private Workout workoutOf(PlannedWorkout planned) {
		return library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

	private static int roundToQuarterHour(double minutes) {
		return (int) (Math.round(minutes / 15) * 15);
	}

}

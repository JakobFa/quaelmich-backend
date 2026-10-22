package io.github.jakobfa.webtechproject.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
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
	static final int MAX_MINUTES = 360;

	private final WorkoutLibrary library;
	private final PlannedWorkoutService plannedWorkoutService;
	private final PlannedWorkoutRepository repository;

	public WorkoutCustomizationService(WorkoutLibrary library, PlannedWorkoutService plannedWorkoutService,
			PlannedWorkoutRepository repository) {
		this.library = library;
		this.plannedWorkoutService = plannedWorkoutService;
		this.repository = repository;
	}

	public Options optionsFor(PlannedWorkout planned) {
		if (planned.isCompleted()) {
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
			if (planned.isCompleted()) {
				throw new NotPossibleException();
			}
			Workout current = workoutOf(planned);
			Choice choice = choiceFor(current, direction).orElseThrow(NotPossibleException::new);
			return apply(planned, current, choice, "Von dir angepasst: " + label(direction) + ".");
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
			if (planned.isCompleted()) {
				throw new NotPossibleException();
			}
			Workout chosen = library.findById(workoutId).orElseThrow(NotPossibleException::new);
			Integer minutes = null;
			if (isVariable(workoutId)) {
				minutes = durationMinutes != null ? durationMinutes : chosen.getDurationInMinutes();
				if (minutes < minimumMinutes(workoutId) || minutes > MAX_MINUTES || minutes % 15 != 0) {
					throw new NotPossibleException();
				}
			}
			return apply(planned, workoutOf(planned), new Choice(workoutId, minutes), "Von dir getauscht.");
		});
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
		int minutes = current.getDurationInMinutes();
		boolean variable = isVariable(id);
		if (current.getType() == WorkoutType.FTP_TEST) {
			return Optional.empty(); // ein Test bleibt ein Test
		}
		if (variable) {
			return variableChoice(id, minutes, direction);
		}
		return switch (direction) {
			case EASIER -> library.easierVariant(current).map(Choice::of)
					.or(() -> Optional.of(new Choice(ENDURANCE, Math.max(60, roundToQuarterHour(minutes)))));
			case HARDER -> harderVariant(current).map(Choice::of);
			case SHORTER -> neighbourByDuration(current, false).map(Choice::of);
			case LONGER -> neighbourByDuration(current, true).map(Choice::of);
		};
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
				if (id == LONG_RIDE) {
					yield Optional.of(new Choice(ENDURANCE, Math.max(60, roundToQuarterHour(minutes * 2 / 3.0))));
				}
				yield Optional.of(new Choice(LONG_RIDE, minutes)); // Tempo-Blöcke weglassen
			}
			case HARDER -> {
				if (id == RECOVERY) {
					yield Optional.of(new Choice(ENDURANCE, Math.max(60, minutes)));
				}
				if (id == ENDURANCE || id == LONG_RIDE) {
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
		return id == RECOVERY || id == ENDURANCE || id == LONG_RIDE || id == LONG_RIDE_WITH_TEMPO;
	}

	static int minimumMinutes(long id) {
		if (id == RECOVERY) {
			return 30;
		}
		return id == ENDURANCE ? 60 : 90;
	}

	private Optional<Workout> harderVariant(Workout workout) {
		List<Workout> variants = library.variantsOf(workout.getType());
		int index = variants.indexOf(library.findById(workout.getId()).orElse(workout));
		return index >= 0 && index < variants.size() - 1 ? Optional.of(variants.get(index + 1)) : Optional.empty();
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

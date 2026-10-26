package io.github.jakobfa.webtechproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;

/**
 * Passt ein Intervall-Workout an eine andere Dauer an, ohne den Hauptteil anzufassen – so bleibt die geplante
 * Belastung gleich, nur der Rahmen ändert sich (wie es ein Trainer machen würde):
 *
 * - Mehr Zeit: Aufwärmen bis 15 min, der Rest als lockere Grundlage (65 % FTP) zwischen Hauptteil und Ausfahren.
 *   Bis 10 min mehr gehen einfach in ein etwas längeres Aufwärmen und Ausfahren.
 * - Weniger Zeit: erst die Pause nach dem letzten Intervall weglassen (das Ausfahren folgt ja direkt), dann das
 *   Ausfahren bis auf 5 min kürzen. Das Aufwärmen bleibt – kürzer als 10 min ist es keins mehr.
 *
 * Geht die Dauer so nicht (zu kurz), ist das Ergebnis leer.
 */
public final class WorkoutFitter {

	static final int MAX_EXTRA_WARMUP_SECONDS = 5 * 60;
	static final int MIN_COOLDOWN_SECONDS = 5 * 60;
	/** Bis hierhin wird die Zusatzzeit auf Aufwärmen und Ausfahren verteilt, darüber gibt es einen Grundlage-Block */
	static final int SMALL_EXTRA_SECONDS = 10 * 60;
	static final int FILLER_PERCENT = 65;

	private WorkoutFitter() {
	}

	/** Lässt sich das Workout überhaupt so anpassen? (Aufwärmen am Anfang, Ausfahren am Ende) */
	public static boolean isFittable(Workout workout) {
		List<WorkoutStep> steps = workout.getSteps();
		return steps.size() >= 3 && steps.getFirst().kind() == StepKind.WARMUP
				&& steps.getLast().kind() == StepKind.COOLDOWN;
	}

	/** Kürzeste mögliche Dauer in Minuten (aufgerundet) */
	public static int minimumMinutes(Workout workout) {
		int seconds = totalSeconds(workout.getSteps()) - removableSeconds(workout.getSteps());
		return (int) Math.ceil(seconds / 60.0);
	}

	public static Optional<Workout> fit(Workout workout, int minutes) {
		if (!isFittable(workout)) {
			return Optional.empty();
		}
		List<WorkoutStep> steps = new ArrayList<>(workout.getSteps());
		int delta = minutes * 60 - totalSeconds(steps);
		if (delta == 0) {
			return Optional.of(workout);
		}
		if (delta > 0) {
			lengthen(steps, delta);
		} else if (!shorten(steps, -delta)) {
			return Optional.empty();
		}
		return Optional.of(new Workout(workout.getId(), workout.getTitle(), workout.getType(),
				workout.getDescription(), List.copyOf(steps)));
	}

	private static void lengthen(List<WorkoutStep> steps, int extra) {
		if (extra <= SMALL_EXTRA_SECONDS) {
			int warmup = (extra + 1) / 2;
			stretch(steps, 0, warmup);
			stretch(steps, steps.size() - 1, extra - warmup);
			return;
		}
		stretch(steps, 0, MAX_EXTRA_WARMUP_SECONDS);
		steps.add(steps.size() - 1,
				WorkoutStep.steady(StepKind.STEADY, extra - MAX_EXTRA_WARMUP_SECONDS, FILLER_PERCENT));
	}

	private static boolean shorten(List<WorkoutStep> steps, int less) {
		if (less > removableSeconds(steps)) {
			return false;
		}
		int lastRecovery = steps.size() - 2;
		if (steps.get(lastRecovery).kind() == StepKind.RECOVERY) {
			int recovery = steps.get(lastRecovery).durationSeconds();
			int cut = Math.min(less, recovery);
			if (cut == recovery) {
				steps.remove(lastRecovery);
			} else {
				stretch(steps, lastRecovery, -cut);
			}
			less -= cut;
		}
		if (less > 0) {
			stretch(steps, steps.size() - 1, -less);
		}
		return true;
	}

	/** Pause direkt vor dem Ausfahren plus alles am Ausfahren über 5 min */
	private static int removableSeconds(List<WorkoutStep> steps) {
		WorkoutStep beforeCooldown = steps.get(steps.size() - 2);
		int recovery = beforeCooldown.kind() == StepKind.RECOVERY ? beforeCooldown.durationSeconds() : 0;
		return recovery + Math.max(0, steps.getLast().durationSeconds() - MIN_COOLDOWN_SECONDS);
	}

	/** Abschnitt um seconds länger (negativ = kürzer); Rampen behalten Start- und Endwert */
	private static void stretch(List<WorkoutStep> steps, int index, int seconds) {
		WorkoutStep step = steps.get(index);
		steps.set(index, new WorkoutStep(step.kind(), step.durationSeconds() + seconds, step.powerStartPercent(),
				step.powerEndPercent()));
	}

	private static int totalSeconds(List<WorkoutStep> steps) {
		return steps.stream().mapToInt(WorkoutStep::durationSeconds).sum();
	}

}

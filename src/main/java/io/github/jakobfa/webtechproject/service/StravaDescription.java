package io.github.jakobfa.webtechproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Der Textblock, den quaelmich unter die Beschreibung einer bestätigten Strava-Fahrt schreibt: welches Training,
 * Ablauf, geplant gegen gefahren, Intervall-Treffer und Bewertung. Der Block steht zwischen festen Markierungen,
 * damit er später ersetzt oder entfernt werden kann, ohne den eigenen Text des Nutzers anzufassen.
 */
public final class StravaDescription {

	static final String START = "── quaelmich ──";
	static final String END = "Plan von quaelmich.com";

	private static final Map<WorkoutType, String> TYPE_LABELS = Map.ofEntries(
			Map.entry(WorkoutType.RECOVERY, "Regeneration"), Map.entry(WorkoutType.ENDURANCE, "Grundlage"),
			Map.entry(WorkoutType.LONG_RIDE, "Lange Ausfahrt"), Map.entry(WorkoutType.TEMPO, "Tempo"),
			Map.entry(WorkoutType.SWEET_SPOT, "Sweet Spot"), Map.entry(WorkoutType.THRESHOLD, "Schwelle"),
			Map.entry(WorkoutType.OVER_UNDER, "Over-Unders"), Map.entry(WorkoutType.VO2MAX, "VO2max"),
			Map.entry(WorkoutType.ANAEROBIC, "Anaerob"), Map.entry(WorkoutType.SPRINT, "Sprint"),
			Map.entry(WorkoutType.FTP_TEST, "FTP-Test"));

	private static final Map<Phase, String> PHASE_LABELS = Map.of(Phase.BASE, "Grundlage", Phase.BUILD, "Aufbau",
			Phase.PEAK, "Spitze", Phase.TAPER, "Tapering", Phase.RECOVERY, "Erholung");

	private static final Map<Assessment, String> ASSESSMENT_LABELS = Map.of(Assessment.EASIER, "leichter als geplant",
			Assessment.AS_PLANNED, "wie geplant", Assessment.HARDER, "härter als geplant", Assessment.MUCH_HARDER,
			"deutlich härter als geplant");

	/** Wie in der App: 1 ganz locker … 10 am Limit */
	private static final int[] RATING_STEPS = { 10, 9, 7, 5, 3, 1 };
	private static final String[] RATING_LABELS = { "am Limit", "sehr hart", "hart", "fordernd", "locker",
			"ganz locker" };

	/**
	 * Alles, was im Block steht. heartRateMode: Ziele als Pulszone statt % FTP; accuracyScore null = nicht
	 * auswertbar; rating null = noch nicht bewertet.
	 */
	public record Content(Workout workout, Phase phase, int week, boolean heartRateMode, Integer actualMinutes,
			Integer actualTss, Integer accuracyScore, boolean accuracyUncertain, Integer rating) {
	}

	private StravaDescription() {
	}

	public static String block(Content content) {
		Workout workout = content.workout();
		List<String> lines = new ArrayList<>();
		lines.add(START);
		String type = TYPE_LABELS.get(workout.getType());
		lines.add(workout.getTitle().contains(type) ? workout.getTitle() : workout.getTitle() + " (" + type + ")");
		lines.add(PHASE_LABELS.get(content.phase()) + "-Phase · Woche " + content.week());
		lines.add("Ablauf" + (content.heartRateMode() ? "" : " (% FTP)") + ": "
				+ sequence(workout.getSteps(), content.heartRateMode()));

		int plannedMinutes = workout.getDurationInMinutes();
		int plannedTss = workout.getTss();
		StringBuilder comparison = new StringBuilder("Geplant " + plannedMinutes + " min / " + plannedTss + " TSS");
		if (content.actualMinutes() != null) {
			comparison.append(" · gefahren ").append(content.actualMinutes()).append(" min");
			if (content.actualTss() != null) {
				comparison.append(" / ").append(content.actualTss()).append(" TSS");
			}
			comparison.append(" → ").append(ASSESSMENT_LABELS.get(
					Assessment.of(plannedTss, content.actualTss(), plannedMinutes, content.actualMinutes())));
		}
		lines.add(comparison.toString());

		if (content.accuracyScore() != null) {
			lines.add("Intervalle: " + content.accuracyScore() + " % getroffen"
					+ (content.accuracyUncertain() ? " (Richtwert)" : ""));
		}
		if (content.rating() != null) {
			lines.add("Gefühlt: " + content.rating() + "/10 – " + ratingLabel(content.rating()));
		}
		lines.add(END);
		return String.join("\n", lines);
	}

	/**
	 * Setzt den Block unter die bisherige Beschreibung. Ein schon vorhandener quaelmich-Block wird ersetzt
	 * (auch mehrfache, falls einmal etwas schiefging). block null = Block nur entfernen.
	 */
	public static String merge(String description, String block) {
		String own = withoutBlocks(description == null ? "" : description).strip();
		if (block == null) {
			return own;
		}
		return own.isEmpty() ? block : own + "\n\n" + block;
	}

	private static String withoutBlocks(String description) {
		String text = description;
		int start;
		while ((start = text.indexOf(START)) >= 0) {
			int end = text.indexOf(END, start);
			// Ende von Hand gelöscht: ab der Markierung ist alles von quaelmich
			int cut = end < 0 ? text.length() : end + END.length();
			text = text.substring(0, start).stripTrailing() + "\n\n" + text.substring(cut).stripLeading();
		}
		return text;
	}

	private static String ratingLabel(int rating) {
		for (int i = 0; i < RATING_STEPS.length; i++) {
			if (rating >= RATING_STEPS[i]) {
				return RATING_LABELS[i];
			}
		}
		return RATING_LABELS[RATING_LABELS.length - 1];
	}

	/** Ablauf in einer Zeile; Wiederholungen werden zusammengefasst, z. B. "4 × (30 s @ 150 % + 30 s locker)" */
	static String sequence(List<WorkoutStep> steps, boolean heartRateMode) {
		List<String> parts = new ArrayList<>();
		int i = 0;
		while (i < steps.size()) {
			int bestLength = 0;
			int bestRepeats = 1;
			for (int length = 1; length <= 4 && i + 2 * length <= steps.size(); length++) {
				int repeats = repeatsAt(steps, i, length);
				if (repeats >= 2 && repeats * length > bestRepeats * bestLength) {
					bestLength = length;
					bestRepeats = repeats;
				}
			}
			if (bestLength == 0) {
				parts.add(step(steps.get(i), heartRateMode));
				i++;
				continue;
			}
			int next = i + bestLength * bestRepeats;
			// Intervall + Pause, und zum Schluss nochmal das Intervall: "3 × 12 min @ 90 % (dazwischen 4 min locker)"
			if (bestLength == 2 && steps.get(i + 1).kind() == StepKind.RECOVERY && next < steps.size()
					&& steps.get(next).equals(steps.get(i))) {
				parts.add((bestRepeats + 1) + " × " + step(steps.get(i), heartRateMode) + " (dazwischen "
						+ step(steps.get(i + 1), heartRateMode) + ")");
				i = next + 1;
				continue;
			}
			List<String> group = steps.subList(i, next).subList(0, bestLength).stream()
					.map(s -> step(s, heartRateMode)).toList();
			parts.add(bestRepeats + " × " + (group.size() == 1 ? group.get(0) : "(" + String.join(" + ", group) + ")"));
			i = next;
		}
		return String.join(" · ", parts);
	}

	private static int repeatsAt(List<WorkoutStep> steps, int from, int length) {
		int repeats = 1;
		while (from + (repeats + 1) * length <= steps.size()
				&& steps.subList(from, from + length).equals(
						steps.subList(from + repeats * length, from + (repeats + 1) * length))) {
			repeats++;
		}
		return repeats;
	}

	private static String step(WorkoutStep step, boolean heartRateMode) {
		String duration = duration(step.durationSeconds());
		return switch (step.kind()) {
			case WARMUP -> duration + " einrollen";
			case COOLDOWN -> duration + " ausrollen";
			case RECOVERY -> duration + " locker";
			default -> duration + " @ " + target(step, heartRateMode);
		};
	}

	private static String target(WorkoutStep step, boolean heartRateMode) {
		if (heartRateMode) {
			return "Z" + HeartRateTargets
					.zoneFor((step.powerStartPercent() + step.powerEndPercent()) / 2.0);
		}
		return step.powerStartPercent() == step.powerEndPercent() ? step.powerStartPercent() + " %"
				: step.powerStartPercent() + "→" + step.powerEndPercent() + " %";
	}

	static String duration(int seconds) {
		if (seconds < 60) {
			return seconds + " s";
		}
		if (seconds % 60 == 0) {
			return seconds / 60 + " min";
		}
		return "%d:%02d min".formatted(seconds / 60, seconds % 60);
	}

}

package io.github.jakobfa.webtechproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.jakobfa.webtechproject.integration.ActivityStream;
import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;

/**
 * Wie genau wurden die Intervalle getroffen? Ein Richtwert, kein Messgerät.
 *
 * Draußen startet niemand exakt nach Plan. Deshalb wird jedes geplante Intervall (direkt aufeinanderfolgende
 * Intervall-Abschnitte wie bei Over-Unders zählen als ein Block) der Reihe nach in der Fahrt gesucht: im
 * Bereich nach dem vorigen Block das Fenster gleicher Länge mit der höchsten Belastung – beim ersten Block
 * großzügig (Einrollen dauert oft länger), danach nur etwa im Rahmen der geplanten Pause. Verglichen wird der
 * Schnitt mit dem Ziel: bis ±5 % "im Ziel". Bei Vollgas-Abschnitten (ab 120 % FTP) zählt nur "zu leicht".
 * Im Puls-Modus wird der Puls im letzten Teil des Blocks bewertet (er braucht, bis er oben ist);
 * kurze Blöcke unter 2 Minuten fallen dort weg.
 */
public final class IntervalAccuracy {

	public enum Basis {
		POWER, HEART_RATE
	}

	public enum Verdict {
		ON_TARGET, SLIGHTLY_HARD, HARD, SLIGHTLY_EASY, EASY
	}

	/** Ein ausgewertetes Intervall: Ziel (targetHigh null = Vollgas, nach oben offen), gefahren, Urteil, Punkte */
	public record Block(int number, int durationSeconds, int targetLow, Integer targetHigh, int actual,
			Verdict verdict, int score) {
	}

	/** score = Akkuratheit in % (nach Dauer gewichtet); uncertain = Intervalle in der Fahrt nicht klar erkennbar */
	public record Result(Basis basis, int score, int onTarget, List<Block> blocks, boolean uncertain, String tip) {
	}

	static final double ON_TARGET = 0.05;
	static final double SLIGHTLY = 0.10;
	/** ab hier "Vollgas": schneller ist nie falsch */
	static final int ALL_OUT_PERCENT = 120;
	/** so weit um die gefundene Stelle herum wird der erste Block noch fein ausgerichtet */
	static final int FIRST_BLOCK_SLACK_SECONDS = 3 * 60;
	/** spätere Blöcke: höchstens doppelte Pause plus 2 Minuten – sonst würde ein Block den nächsten "finden" */
	static final int LATER_BLOCK_SLACK_SECONDS = 2 * 60;

	private IntervalAccuracy() {
	}

	/** Geplanter Block: Startsekunde laut Plan, Dauer, Ziel in % FTP (zeitgewichtet) und die Abschnitte */
	record PlannedBlock(int plannedStart, int duration, double targetPercent, List<WorkoutStep> steps) {
	}

	/** Hat das Workout Intervalle, die man in einer Fahrt suchen kann? */
	public static boolean hasIntervals(Workout workout) {
		return !blocksOf(workout).isEmpty();
	}

	static List<PlannedBlock> blocksOf(Workout workout) {
		List<PlannedBlock> blocks = new ArrayList<>();
		int second = 0;
		List<WorkoutStep> current = new ArrayList<>();
		int currentStart = 0;
		for (WorkoutStep step : workout.getSteps()) {
			if (step.kind() == StepKind.INTERVAL) {
				if (current.isEmpty()) {
					currentStart = second;
				}
				current.add(step);
			} else if (!current.isEmpty()) {
				blocks.add(block(currentStart, current));
				current = new ArrayList<>();
			}
			second += step.durationSeconds();
		}
		if (!current.isEmpty()) {
			blocks.add(block(currentStart, current));
		}
		return blocks;
	}

	private static PlannedBlock block(int start, List<WorkoutStep> steps) {
		int duration = steps.stream().mapToInt(WorkoutStep::durationSeconds).sum();
		double weighted = steps.stream()
				.mapToDouble(step -> step.durationSeconds() * (step.powerStartPercent() + step.powerEndPercent()) / 2.0)
				.sum();
		return new PlannedBlock(start, duration, weighted / duration, List.copyOf(steps));
	}

	/** Nach Watt (braucht FTP und Wattmesser) – leer, wenn das Workout keine Intervalle hat */
	public static Optional<Result> byPower(Workout workout, ActivityStream stream, int ftp) {
		List<PlannedBlock> planned = blocksOf(workout);
		if (planned.isEmpty() || !stream.hasWatts()) {
			return Optional.empty();
		}
		List<Window> windows = match(planned, stream.watts());
		if (windows == null) {
			return Optional.empty();
		}
		List<Block> blocks = new ArrayList<>();
		boolean uncertain = false;
		for (int i = 0; i < planned.size(); i++) {
			PlannedBlock block = planned.get(i);
			Window window = windows.get(i);
			int target = (int) Math.round(block.targetPercent() * ftp / 100);
			boolean allOut = block.targetPercent() >= ALL_OUT_PERCENT;
			double deviation = (double) window.average() / target - 1;
			if (allOut) {
				deviation = Math.min(0, deviation);
			}
			blocks.add(new Block(i + 1, block.duration(), (int) Math.round(target * (1 - ON_TARGET)),
					allOut ? null : (int) Math.round(target * (1 + ON_TARGET)), window.average(),
					verdict(deviation), score(deviation)));
			// ein hartes Intervall, das sich kaum von der Zeit davor abhebt, ist wohl nicht wirklich gefunden
			if (block.targetPercent() >= 88 && window.before() > 0 && window.average() < window.before() * 1.05) {
				uncertain = true;
			}
		}
		return Optional.of(result(Basis.POWER, blocks, uncertain));
	}

	/** Nach Puls: nur Blöcke ab 2 Minuten, bewertet im letzten Drittel */
	public static Optional<Result> byHeartRate(Workout workout, ActivityStream stream, HeartRateTargets targets) {
		List<PlannedBlock> planned = blocksOf(workout);
		if (planned.isEmpty() || !stream.hasHeartRate()) {
			return Optional.empty();
		}
		List<Window> windows = match(planned, stream.heartRate());
		if (windows == null) {
			return Optional.empty();
		}
		List<Block> blocks = new ArrayList<>();
		for (int i = 0; i < planned.size(); i++) {
			PlannedBlock block = planned.get(i);
			HeartRateTargets.Range range = block.duration() >= HeartRateTargets.MIN_SECONDS_FOR_TARGET
					? targets.forStep(block.steps().getFirst())
					: null;
			if (range == null) {
				continue;
			}
			Window window = windows.get(i);
			int lastThird = Math.max(1, block.duration() / 3);
			int actual = average(stream.heartRate(), window.start() + block.duration() - lastThird, lastThird);
			double deviation = actual > range.high() ? (double) actual / range.high() - 1
					: actual < range.low() ? (double) actual / range.low() - 1 : 0;
			blocks.add(new Block(i + 1, block.duration(), range.low(), range.high(), actual, verdict(deviation),
					score(deviation)));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(result(Basis.HEART_RATE, blocks, false));
	}

	// ---------- Blöcke in der Fahrt finden ----------

	/** start = Sekunde in der Fahrt; average = Schnitt im Fenster; before = Schnitt der 3 Minuten davor */
	record Window(int start, int average, int before) {
	}

	/**
	 * Findet die Blöcke in der Fahrt; null, wenn die Fahrt zu kurz für alle Blöcke ist.
	 *
	 * Erst wird das ganze Muster (Blöcke mit den geplanten Pausen) über die komplette Fahrt geschoben – so
	 * stören An- und Abfahrt nicht, egal wie lang sie sind. Gewählt wird die Stelle, an der sich die Blöcke am
	 * deutlichsten von den Pausen abheben; ein langer, gleichmäßig harter Anstieg auf der Anfahrt passt dadurch
	 * schlechter als die echten Intervalle. Danach wird jeder Block einzeln fein ausgerichtet (längere Pausen,
	 * Ampeln …).
	 */
	static List<Window> match(List<PlannedBlock> planned, Integer[] values) {
		long[] prefix = new long[values.length + 1];
		for (int i = 0; i < values.length; i++) {
			prefix[i + 1] = prefix[i] + (values[i] == null ? 0 : values[i]);
		}
		Integer anchor = anchor(planned, prefix, values.length);
		if (anchor == null) {
			return null;
		}
		List<Window> windows = new ArrayList<>();
		int pointer = Math.max(0, anchor - FIRST_BLOCK_SLACK_SECONDS);
		int plannedEnd = planned.getFirst().plannedStart();
		for (PlannedBlock block : planned) {
			int gap = Math.max(0, block.plannedStart() - plannedEnd);
			int latestStart = windows.isEmpty()
					? Math.min(values.length - block.duration(), anchor + FIRST_BLOCK_SLACK_SECONDS)
					: Math.min(values.length - block.duration(), pointer + 2 * gap + LATER_BLOCK_SLACK_SECONDS);
			if (latestStart < pointer) {
				return null;
			}
			int best = pointer;
			long bestSum = Long.MIN_VALUE;
			for (int start = pointer; start <= latestStart; start++) {
				long sum = prefix[start + block.duration()] - prefix[start];
				if (sum > bestSum) {
					bestSum = sum;
					best = start;
				}
			}
			int beforeLength = Math.min(180, best);
			windows.add(new Window(best, (int) Math.round((double) bestSum / block.duration()),
					beforeLength == 0 ? 0 : average(values, best - beforeLength, beforeLength)));
			pointer = best + block.duration();
			plannedEnd = block.plannedStart() + block.duration();
		}
		return windows;
	}

	/** Startsekunde des ersten Blocks an der am besten passenden Stelle; null, wenn das Muster nicht reinpasst */
	private static Integer anchor(List<PlannedBlock> planned, long[] prefix, int length) {
		int first = planned.getFirst().plannedStart();
		PlannedBlock last = planned.getLast();
		int patternLength = last.plannedStart() + last.duration() - first;
		if (patternLength > length) {
			return null;
		}
		Integer best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int start = 0; start + patternLength <= length; start++) {
			long blockSum = 0;
			int blockSeconds = 0;
			long gapSum = 0;
			int gapSeconds = 0;
			int previousEnd = -1;
			for (PlannedBlock block : planned) {
				int from = start + block.plannedStart() - first;
				blockSum += prefix[from + block.duration()] - prefix[from];
				blockSeconds += block.duration();
				if (previousEnd >= 0 && from > previousEnd) {
					gapSum += prefix[from] - prefix[previousEnd];
					gapSeconds += from - previousEnd;
				}
				previousEnd = from + block.duration();
			}
			double score = (double) blockSum / blockSeconds - (gapSeconds == 0 ? 0 : (double) gapSum / gapSeconds);
			if (score > bestScore) {
				bestScore = score;
				best = start;
			}
		}
		return best;
	}

	private static int average(Integer[] values, int from, int length) {
		long sum = 0;
		for (int i = from; i < from + length && i < values.length; i++) {
			sum += values[i] == null ? 0 : values[i];
		}
		return (int) Math.round((double) sum / length);
	}

	// ---------- Bewertung ----------

	static Verdict verdict(double deviation) {
		if (Math.abs(deviation) <= ON_TARGET) {
			return Verdict.ON_TARGET;
		}
		if (deviation > 0) {
			return deviation <= SLIGHTLY ? Verdict.SLIGHTLY_HARD : Verdict.HARD;
		}
		return -deviation <= SLIGHTLY ? Verdict.SLIGHTLY_EASY : Verdict.EASY;
	}

	/** ±5 % → 90, ±10 % → 80, ±25 % → 50 */
	static int score(double deviation) {
		return (int) Math.round(Math.clamp(100 - Math.abs(deviation) * 200, 0, 100));
	}

	private static Result result(Basis basis, List<Block> blocks, boolean uncertain) {
		int totalSeconds = blocks.stream().mapToInt(Block::durationSeconds).sum();
		int score = (int) Math.round(blocks.stream()
				.mapToDouble(block -> (double) block.score() * block.durationSeconds()).sum() / totalSeconds);
		int onTarget = (int) blocks.stream().filter(block -> block.verdict() == Verdict.ON_TARGET).count();
		return new Result(basis, score, onTarget, List.copyOf(blocks), uncertain, tipFor(blocks, score));
	}

	static String tipFor(List<Block> blocks, int score) {
		boolean anyHard = blocks.stream().anyMatch(b -> b.verdict() == Verdict.HARD || b.verdict() == Verdict.SLIGHTLY_HARD);
		boolean allHard = blocks.stream().allMatch(b -> b.verdict() == Verdict.HARD || b.verdict() == Verdict.SLIGHTLY_HARD);
		boolean allEasy = blocks.stream().allMatch(b -> b.verdict() == Verdict.EASY || b.verdict() == Verdict.SLIGHTLY_EASY);
		Block last = blocks.getLast();
		boolean faded = blocks.size() >= 3 && anyHard
				&& (last.verdict() == Verdict.EASY || last.verdict() == Verdict.SLIGHTLY_EASY);
		if (faded) {
			return "Zu schnell angegangen – am Ende ging die Kraft aus. Die ersten Intervalle etwas ruhiger fahren.";
		}
		if (allHard) {
			return "Durchweg über dem Ziel. Das fühlt sich gut an, kostet aber Erholung – der Zielbereich ist bewusst so gewählt.";
		}
		if (allEasy) {
			return "Durchweg unter dem Ziel. Nicht dein Tag – oder die FTP ist etwas zu hoch eingestellt.";
		}
		if (score >= 90) {
			return "Sehr präzise gefahren.";
		}
		return null;
	}

}

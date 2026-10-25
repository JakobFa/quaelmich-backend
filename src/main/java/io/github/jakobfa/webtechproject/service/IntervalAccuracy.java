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

	/**
	 * Ein ausgewertetes Intervall: Ziel (targetHigh null = Vollgas, nach oben offen), gefahren, Urteil, Punkte.
	 * startSecond = wo es in der Fahrt gefunden wurde (Sekunden seit Start, Stillstand mitgezählt).
	 */
	public record Block(int number, int startSecond, int durationSeconds, int targetLow, Integer targetHigh,
			int actual, Verdict verdict, int score) {
	}

	/** So viele Sekunden fasst ein Punkt der Kurve zusammen */
	public static final int TRACE_SECONDS = 10;

	/**
	 * score = Akkuratheit in % (nach Dauer gewichtet); uncertain = Intervalle in der Fahrt nicht klar erkennbar.
	 * trace = Watt bzw. Puls der ganzen Fahrt, ein Wert je {@link #TRACE_SECONDS} Sekunden (null = Stillstand) –
	 * damit die App die Kurve über die gefundenen Intervalle legen kann.
	 */
	public record Result(Basis basis, int score, int onTarget, List<Block> blocks, boolean uncertain, String tip,
			List<Integer> trace) {

		public Result(Basis basis, int score, int onTarget, List<Block> blocks, boolean uncertain, String tip) {
			this(basis, score, onTarget, blocks, uncertain, tip, List.of());
		}
	}

	static final double ON_TARGET = 0.05;
	static final double SLIGHTLY = 0.10;
	/** ab hier "Vollgas": schneller ist nie falsch */
	static final int ALL_OUT_PERCENT = 120;
	/** so viel länger als geplant darf eine Pause draußen sein (Ampel, länger ausrollen) … */
	static final int MAX_LONGER_REST_SECONDS = 3 * 60;
	/** … und so viel kürzer */
	static final int MAX_SHORTER_REST_SECONDS = 60;
	/**
	 * Leichte Vorliebe für die geplanten Pausenlängen: jede Minute Abweichung kostet so viel wie 3 W weniger
	 * Abstand zur Pause. Ampeln und längeres Ausrollen bleiben möglich – bei Gleichstand gewinnt aber der Plan
	 * (sonst könnte z. B. ein Anstieg auf der Anfahrt den ersten Tempo-Block "spielen").
	 */
	static final double WATTS_PER_MINUTE_OFF_PLAN = 3;
	/** Schnitt so vieler Sekunden vor dem ersten Block als Vergleich ("hebt sich ab?") */
	static final int LEAD_IN_SECONDS = 2 * 60;

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
			blocks.add(new Block(i + 1, window.start(), block.duration(), (int) Math.round(target * (1 - ON_TARGET)),
					allOut ? null : (int) Math.round(target * (1 + ON_TARGET)), window.average(),
					verdict(deviation), score(deviation)));
			// ein hartes Intervall, das sich kaum von der Zeit davor abhebt, ist wohl nicht wirklich gefunden
			if (block.targetPercent() >= 88 && window.before() > 0 && window.average() < window.before() * 1.05) {
				uncertain = true;
			}
		}
		return Optional.of(result(Basis.POWER, blocks, uncertain, stream.watts()));
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
			blocks.add(new Block(i + 1, window.start(), block.duration(), range.low(), range.high(), actual,
					verdict(deviation), score(deviation)));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(result(Basis.HEART_RATE, blocks, false, stream.heartRate()));
	}

	// ---------- Blöcke in der Fahrt finden ----------

	/** start = Sekunde in der Fahrt; average = Schnitt im Fenster; before = Schnitt der 3 Minuten davor */
	record Window(int start, int average, int before) {
	}

	/**
	 * Summen und Anzahl der Sekunden mit Wert, jeweils ab Fahrtbeginn. Sekunden ohne Wert sind Stillstand
	 * (Ampel, Pause) – sie zählen bei Mittelwerten nicht als 0 Watt. Sonst sähe eine Ampel wie eine perfekte
	 * Erholung zwischen zwei Intervallen aus, und das ganze Muster würde verrutschen.
	 */
	record Sums(long[] sum, int[] count) {

		static Sums of(Integer[] values) {
			long[] sum = new long[values.length + 1];
			int[] count = new int[values.length + 1];
			for (int i = 0; i < values.length; i++) {
				sum[i + 1] = sum[i] + (values[i] == null ? 0 : values[i]);
				count[i + 1] = count[i] + (values[i] == null ? 0 : 1);
			}
			return new Sums(sum, count);
		}

		long sum(int from, int to) {
			return sum[to] - sum[from];
		}

		int count(int from, int to) {
			return count[to] - count[from];
		}

		/** Schnitt über die gefahrenen Sekunden; 0, wenn es keine gibt */
		int mean(int from, int to) {
			int seconds = count(from, to);
			return seconds == 0 ? 0 : (int) Math.round((double) sum(from, to) / seconds);
		}
	}

	/**
	 * Findet die Blöcke in der Fahrt; null, wenn die Fahrt zu kurz für alle Blöcke ist.
	 *
	 * Gesucht wird die Anordnung aller Blöcke, bei der sich jeder Block am deutlichsten von der Zeit davor abhebt
	 * (sonst die Pause dazwischen; vor dem ersten und nach dem letzten Block ein Stück so lang wie Pause plus
	 * Block). Der erste Block darf überall in der
	 * Fahrt liegen – An- und Abfahrt stören also nicht. Jede Pause darf draußen kürzer oder länger sein als
	 * geplant (Ampel, länger ausrollen), aber nie so lang, dass ein Block übersprungen wird. Die beste
	 * Gesamt-Anordnung wird mit dynamischer Programmierung bestimmt; gewichtet wird nach Blockdauer, damit
	 * lange Blöcke mehr zählen als kurze.
	 */
	static List<Window> match(List<PlannedBlock> planned, Integer[] values) {
		Sums sums = Sums.of(values);
		int length = values.length;
		int blocks = planned.size();
		int[] duration = new int[blocks];
		int[] gap = new int[blocks]; // geplante Pause vor Block i (0 für den ersten)
		int minimumLength = 0;
		for (int i = 0; i < blocks; i++) {
			duration[i] = planned.get(i).duration();
			gap[i] = i == 0 ? 0
					: Math.max(0, planned.get(i).plannedStart() - planned.get(i - 1).plannedStart() - duration[i - 1]);
			minimumLength += duration[i] + Math.max(0, gap[i] - Math.min(gap[i] / 2, MAX_SHORTER_REST_SECONDS));
		}
		if (minimumLength > length) {
			return null;
		}
		double rideMean = sums.mean(0, length);
		// vor dem ersten und nach dem letzten Block eine Pause plus einen Block weit vergleichen: Liegt dort noch
		// ein Block, ist das ganze Muster verrutscht (z. B. ein Anstieg auf der Anfahrt als "erster Block")
		int context = Math.max(LEAD_IN_SECONDS, blocks > 1 ? gap[1] + duration[0] : 0);

		double[][] best = new double[blocks][length];
		int[][] previous = new int[blocks][length];
		for (double[] row : best) {
			java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
		}
		for (int start = 0; start + duration[0] <= length; start++) {
			Double block = movingMean(sums, start, start + duration[0]);
			if (block != null) {
				int leadIn = Math.max(0, start - context);
				best[0][start] = duration[0] * (block - context(sums, leadIn, start, rideMean));
			}
		}
		for (int i = 1; i < blocks; i++) {
			int shorter = Math.min(gap[i] / 2, MAX_SHORTER_REST_SECONDS);
			// länger ja – aber nie um einen ganzen Block samt Pause, sonst könnte einer übersprungen werden
			int longer = Math.min(MAX_LONGER_REST_SECONDS, Math.max(1, gap[i] + duration[i] - 1));
			for (int prior = 0; prior < length; prior++) {
				if (best[i - 1][prior] == Double.NEGATIVE_INFINITY) {
					continue;
				}
				int priorEnd = prior + duration[i - 1];
				int from = priorEnd + gap[i] - shorter;
				int to = Math.min(length - duration[i], priorEnd + gap[i] + longer);
				for (int start = Math.max(priorEnd, from); start <= to; start++) {
					Double block = movingMean(sums, start, start + duration[i]);
					if (block == null) {
						continue;
					}
					double rest = start > priorEnd ? meanOr(sums, priorEnd, start, rideMean) : rideMean;
					double offPlan = Math.abs(start - priorEnd - gap[i]) / 60.0 * WATTS_PER_MINUTE_OFF_PLAN;
					double total = best[i - 1][prior] + duration[i] * (block - rest - offPlan);
					if (total > best[i][start]) {
						best[i][start] = total;
						previous[i][start] = prior;
					}
				}
			}
		}

		// nach dem letzten Block: hebt er sich auch von dem ab, was danach kommt?
		int last = -1;
		double lastTotal = Double.NEGATIVE_INFINITY;
		int lastDuration = duration[blocks - 1];
		for (int start = 0; start + lastDuration <= length; start++) {
			if (best[blocks - 1][start] == Double.NEGATIVE_INFINITY) {
				continue;
			}
			int end = start + lastDuration;
			double block = sums.mean(start, end);
			double after = context(sums, end, Math.min(length, end + context), rideMean);
			double total = best[blocks - 1][start] + lastDuration * (block - after);
			if (total > lastTotal) {
				lastTotal = total;
				last = start;
			}
		}
		if (last < 0) {
			return null;
		}
		int[] starts = new int[blocks];
		starts[blocks - 1] = last;
		for (int i = blocks - 1; i > 0; i--) {
			starts[i - 1] = previous[i][starts[i]];
		}
		List<Window> windows = new ArrayList<>();
		for (int i = 0; i < blocks; i++) {
			int start = starts[i];
			int beforeLength = Math.min(180, start);
			windows.add(new Window(start, sums.mean(start, start + duration[i]),
					beforeLength == 0 ? 0 : sums.mean(start - beforeLength, start)));
		}
		return windows;
	}

	/** Schnitt der gefahrenen Sekunden; null, wenn im Fenster überwiegend gestanden wurde (das ist kein Block) */
	private static Double movingMean(Sums sums, int from, int to) {
		int moving = sums.count(from, to);
		return moving * 2 < to - from ? null : (double) sums.sum(from, to) / moving;
	}

	/**
	 * Vergleichsstück vor dem ersten bzw. nach dem letzten Block: erst ab einer gefahrenen Minute aussagekräftig
	 * (sonst wäre z. B. die allererste Sekunde der Fahrt ein "perfekter" Kontrast) – vorher zählt neutral der
	 * Schnitt der Fahrt.
	 */
	private static double context(Sums sums, int from, int to, double fallback) {
		return sums.count(from, to) < 60 ? fallback : (double) sums.sum(from, to) / sums.count(from, to);
	}

	/** Schnitt der gefahrenen Sekunden – nur gestanden zählt neutral wie der Schnitt der ganzen Fahrt */
	private static double meanOr(Sums sums, int from, int to, double fallback) {
		int moving = sums.count(from, to);
		return moving == 0 ? fallback : (double) sums.sum(from, to) / moving;
	}

	private static int average(Integer[] values, int from, int length) {
		int to = Math.min(from + length, values.length);
		return Sums.of(java.util.Arrays.copyOfRange(values, from, to)).mean(0, to - from);
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

	private static Result result(Basis basis, List<Block> blocks, boolean uncertain, Integer[] values) {
		int totalSeconds = blocks.stream().mapToInt(Block::durationSeconds).sum();
		int score = (int) Math.round(blocks.stream()
				.mapToDouble(block -> (double) block.score() * block.durationSeconds()).sum() / totalSeconds);
		int onTarget = (int) blocks.stream().filter(block -> block.verdict() == Verdict.ON_TARGET).count();
		return new Result(basis, score, onTarget, List.copyOf(blocks), uncertain, tipFor(blocks, score),
				trace(values));
	}

	/** Schnitt je 10 Sekunden über die gefahrenen Sekunden; null, wenn in den 10 Sekunden nur gestanden wurde */
	static List<Integer> trace(Integer[] values) {
		Sums sums = Sums.of(values);
		List<Integer> trace = new ArrayList<>();
		for (int from = 0; from < values.length; from += TRACE_SECONDS) {
			int to = Math.min(from + TRACE_SECONDS, values.length);
			trace.add(sums.count(from, to) == 0 ? null : sums.mean(from, to));
		}
		return java.util.Collections.unmodifiableList(trace);
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

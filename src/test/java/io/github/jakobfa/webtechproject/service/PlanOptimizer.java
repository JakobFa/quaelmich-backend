package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sucht bessere Stellschrauben ({@link PlanParameters}) für das Leistungsmodell: Bergsteigen mit zufälligen Schritten
 * und Neustarts, solange das Zeitbudget reicht. Bewertet wird an planfolgenden Zufallsfahrern (Trainings-Satz);
 * am Ende prüft ein zweiter, nie gesehener Satz (Prüf-Satz), ob der Vorteil echt ist, und ein Satz aller Zufallsfahrer
 * (Robustheit), dass keine neuen harten Befunde entstehen. Dazu, welche Stellschraube wie viel bewirkt.
 *
 * Das Ergebnis ist ein Vorschlag, keine Änderung: Übernommen wird nur nach sportlicher Prüfung, per PR.
 *
 * Aufruf: ./gradlew optimizePlan [-PbudgetSeconds=600] [-PtrainRiders=500] [-PholdoutRiders=1000]
 */
public final class PlanOptimizer {

	/** Eine Stellschraube mit erlaubtem Bereich und Schrittweite */
	record Dim(String name, double min, double max, double step) {

		double clamp(double value) {
			double snapped = min + Math.round((value - min) / step) * step;
			return Math.max(min, Math.min(max, Math.round(snapped * 1000) / 1000.0));
		}
	}

	/** Bereiche, in denen gesucht wird – sportlich vertretbar, nicht beliebig */
	static final List<Dim> DIMS = List.of(new Dim("loadWeeksPerBlock", 2, 4, 1), new Dim("volumeStep", 0.02, 0.1, 0.01),
			new Dim("recoveryVolume", 0.4, 0.8, 0.05), new Dim("recoveryAcuteRatio", 0.6, 0.9, 0.05),
			new Dim("acuteChronicRatio", 1.05, 1.35, 0.01), new Dim("restedBlockRatio", 0.75, 1.0, 0.05),
			new Dim("spikeRatio", 1.2, 1.6, 0.05), new Dim("spikeMinExtraMinutes", 30, 180, 15),
			new Dim("maxLoadWeeksWithoutRecovery", 4, 7, 1), new Dim("memoryWeeklyDecay", 0.85, 1.0, 0.01),
			new Dim("taperWeeks", 1, 3, 1));

	static final long TRAIN_SEED = 1;
	static final long HOLDOUT_SEED = 1_000_000;
	static final long ROBUST_SEED = 2_000_000;
	static final double MIN_IMPROVEMENT = 0.05;
	static final int PATIENCE = 30;

	private PlanOptimizer() {
	}

	record Result(PlanParameters best, ParameterEvaluation.Evaluation trainDefault,
			ParameterEvaluation.Evaluation trainBest, int evaluations, List<String> log) {
	}

	/** Die Suche selbst (ohne Prüf-Satz und Bericht) */
	static Result search(long budgetSeconds, int trainRiders, long randomSeed) {
		long deadline = System.nanoTime() + budgetSeconds * 1_000_000_000L;
		Random random = new Random(randomSeed);
		Map<PlanParameters, ParameterEvaluation.Evaluation> cache = new HashMap<>();
		java.util.function.Function<PlanParameters, ParameterEvaluation.Evaluation> eval = p -> cache.computeIfAbsent(p,
				q -> ParameterEvaluation.evaluate(q, TRAIN_SEED, trainRiders, true));
		List<String> log = new ArrayList<>();

		ParameterEvaluation.Evaluation start = eval.apply(PlanParameters.DEFAULT);
		ParameterEvaluation.Evaluation best = start;
		PlanParameters current = PlanParameters.DEFAULT;
		double currentValue = start.objective();
		int sinceImprovement = 0;
		while (System.nanoTime() < deadline) {
			// meist kleine Schritte um den aktuellen Punkt, ab und zu ein Neustart vom besten oder ganz woanders
			PlanParameters base = sinceImprovement > PATIENCE ? best.parameters() : current;
			double scale = sinceImprovement > PATIENCE ? 3 : 1;
			if (sinceImprovement > PATIENCE * 2) {
				base = randomPoint(random);
				sinceImprovement = 0;
			}
			PlanParameters candidate = neighbour(base, random, scale);
			ParameterEvaluation.Evaluation evaluation = eval.apply(candidate);
			if (evaluation.objective() > currentValue + MIN_IMPROVEMENT || base != current) {
				current = candidate;
				currentValue = evaluation.objective();
			}
			if (evaluation.objective() > best.objective() + MIN_IMPROVEMENT) {
				best = evaluation;
				sinceImprovement = 0;
				log.add("%d: %.2f – %s".formatted(cache.size(), best.objective(), changes(best.parameters())));
			} else {
				sinceImprovement++;
			}
		}
		return new Result(best.parameters(), start, best, cache.size(), log);
	}

	/** Ein bis drei Stellschrauben um ein paar Schritte verändern */
	static PlanParameters neighbour(PlanParameters base, Random random, double scale) {
		Map<String, Double> values = values(base);
		int changes = 1 + random.nextInt(3);
		for (int i = 0; i < changes; i++) {
			Dim dim = DIMS.get(random.nextInt(DIMS.size()));
			int steps = (1 + random.nextInt((int) (2 * scale))) * (random.nextBoolean() ? 1 : -1);
			values.put(dim.name(), dim.clamp(values.get(dim.name()) + steps * dim.step()));
		}
		return build(values);
	}

	static PlanParameters randomPoint(Random random) {
		Map<String, Double> values = values(PlanParameters.DEFAULT);
		for (Dim dim : DIMS) {
			values.put(dim.name(), dim.clamp(dim.min() + random.nextDouble() * (dim.max() - dim.min())));
		}
		return build(values);
	}

	static Map<String, Double> values(PlanParameters p) {
		Map<String, Double> values = new HashMap<>();
		for (RecordComponent c : PlanParameters.class.getRecordComponents()) {
			try {
				values.put(c.getName(), ((Number) c.getAccessor().invoke(p)).doubleValue());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		return values;
	}

	static PlanParameters build(Map<String, Double> values) {
		StringBuilder text = new StringBuilder();
		for (RecordComponent c : PlanParameters.class.getRecordComponents()) {
			double v = values.get(c.getName());
			text.append(text.isEmpty() ? "" : ",").append(c.getName()).append('=')
					.append(c.getType() == int.class ? String.valueOf((int) Math.round(v)) : String.valueOf(v));
		}
		return ParameterEvaluation.parse(text.toString());
	}

	/** Was gegenüber den App-Werten anders ist, z. B. "recoveryVolume 0.6 → 0.55" */
	static String changes(PlanParameters p) {
		Map<String, Double> app = values(PlanParameters.DEFAULT);
		Map<String, Double> now = values(p);
		List<String> parts = new ArrayList<>();
		for (Dim dim : DIMS) {
			if (Math.abs(app.get(dim.name()) - now.get(dim.name())) > 1e-9) {
				parts.add("%s %s → %s".formatted(dim.name(), fmt(app.get(dim.name())), fmt(now.get(dim.name()))));
			}
		}
		return parts.isEmpty() ? "keine Änderung" : String.join(", ", parts);
	}

	private static String fmt(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	public static void main(String[] args) throws IOException {
		Path target = Path.of(args.length > 0 ? args[0] : "build/plan-optimization.md");
		long budget = args.length > 1 && !args[1].isBlank() ? Long.parseLong(args[1]) : 600;
		int trainRiders = args.length > 2 && !args[2].isBlank() ? Integer.parseInt(args[2]) : 500;
		int holdoutRiders = args.length > 3 && !args[3].isBlank() ? Integer.parseInt(args[3]) : 1000;
		long started = System.nanoTime();

		Result result = search(budget, trainRiders, LocalDate.now().toEpochDay());
		PlanParameters best = result.best();
		var holdoutApp = ParameterEvaluation.evaluate(PlanParameters.DEFAULT, HOLDOUT_SEED, holdoutRiders, true);
		var holdoutBest = ParameterEvaluation.evaluate(best, HOLDOUT_SEED, holdoutRiders, true);
		var robustApp = ParameterEvaluation.evaluate(PlanParameters.DEFAULT, ROBUST_SEED, holdoutRiders, false);
		var robustBest = ParameterEvaluation.evaluate(best, ROBUST_SEED, holdoutRiders, false);
		boolean better = holdoutBest.objective() > holdoutApp.objective() + MIN_IMPROVEMENT
				&& robustBest.hardPerRider() <= robustApp.hardPerRider();

		StringBuilder out = new StringBuilder("# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)\n\n");
		out.append("""
				Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag \
				in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach \
				sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

				""");
		out.append("%d Parameter-Sätze bewertet in %.0f min (Trainings-Satz %d Fahrer, Prüf-Satz %d).%n%n".formatted(
				result.evaluations(), (System.nanoTime() - started) / 6e10, trainRiders, holdoutRiders));
		out.append("**Ergebnis: ").append(better ? "Vorschlag ist auch auf dem Prüf-Satz besser."
				: "Kein belastbarer Vorteil gegenüber den App-Werten.").append("**\n\n");
		out.append("Änderungen: ").append(changes(best)).append("\n\n");
		out.append("| | App-Werte | Vorschlag |\n|---|---|---|\n");
		out.append(row("Gesamtwert Trainings-Satz", result.trainDefault().objective(), result.trainBest().objective()));
		out.append(row("Gesamtwert Prüf-Satz", holdoutApp.objective(), holdoutBest.objective()));
		out.append(row("Form-Zuwachs im Mittel (Prüf-Satz)", holdoutApp.meanFormGain(), holdoutBest.meanFormGain()));
		out.append(row("schwächste 10 % (Prüf-Satz)", holdoutApp.p10Score(), holdoutBest.p10Score()));
		out.append(row("Überlastung im Mittel (Prüf-Satz)", holdoutApp.meanOverload(), holdoutBest.meanOverload()));
		out.append(row("Warnungen je Fahrer (Prüf-Satz)", holdoutApp.warningsPerRider(), holdoutBest.warningsPerRider()));
		out.append(row("harte Befunde je Fahrer (alle Zufallsfahrer)", robustApp.hardPerRider(), robustBest.hardPerRider()));
		out.append(row("Warnungen je Fahrer (alle Zufallsfahrer)", robustApp.warningsPerRider(),
				robustBest.warningsPerRider()));

		out.append("\n## Was jede Stellschraube bewirkt (Prüf-Satz, ausgehend von den App-Werten)\n\n");
		out.append("| Stellschraube | App-Wert | kleiner | Gesamtwert | größer | Gesamtwert |\n|---|---|---|---|---|---|\n");
		Map<String, Double> app = values(PlanParameters.DEFAULT);
		for (Dim dim : DIMS) {
			double value = app.get(dim.name());
			double low = dim.clamp(value - 2 * dim.step());
			double high = dim.clamp(value + 2 * dim.step());
			out.append("| %s | %s | %s | %s | %s | %s |\n".formatted(dim.name(), fmt(value), fmt(low),
					delta(dim, low, holdoutApp, holdoutRiders), fmt(high), delta(dim, high, holdoutApp, holdoutRiders)));
		}
		out.append("\n## Verlauf der Suche\n\n");
		result.log().forEach(line -> out.append("- ").append(line).append('\n'));
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
		System.out.println(out);
	}

	private static String row(String label, double app, double best) {
		return "| %s | %.2f | %.2f |%n".formatted(label, app, best);
	}

	private static String delta(Dim dim, double value, ParameterEvaluation.Evaluation base, int riders) {
		Map<String, Double> values = values(PlanParameters.DEFAULT);
		if (Math.abs(values.get(dim.name()) - value) < 1e-9) {
			return "–";
		}
		values.put(dim.name(), value);
		var evaluation = ParameterEvaluation.evaluate(build(values), HOLDOUT_SEED, riders, true);
		return "%+.2f".formatted(evaluation.objective() - base.objective());
	}
}

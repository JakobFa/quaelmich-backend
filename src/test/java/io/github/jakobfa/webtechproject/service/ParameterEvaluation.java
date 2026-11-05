package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Bewertet einen Satz Stellschrauben ({@link PlanParameters}) an einem festen Satz Zufallsfahrer: Wie gut ist die Form
 * am Ziel-Tag laut {@link PerformanceModel}, und wie viele Befunde hat die Simulation? Grundlage für die Optimierung –
 * derselbe Fahrer-Satz für jeden Parameter-Satz, damit die Zahlen vergleichbar sind.
 *
 * Aufruf: ./gradlew evaluatePlan [-Pparams="loadWeeksPerBlock=4,recoveryVolume=0.5"] [-Priders=1000] [-PfirstSeed=1]
 */
public final class ParameterEvaluation {

	/** Abzug im Gesamtwert je hartem Befund und je Warnung (pro Fahrer gemittelt) */
	static final double HARD_WEIGHT = 50;
	static final double WARNING_WEIGHT = 1;

	private ParameterEvaluation() {
	}

	/** Ergebnis für einen Parameter-Satz über alle Fahrer */
	record Evaluation(PlanParameters parameters, int riders, double meanScore, double p10Score, double meanFormGain,
			double meanOverload, double hardPerRider, double warningsPerRider, int crashes) {

		/** Ein Wert zum Vergleichen: mittlerer Score, die schwächsten 10 % mit halbem Gewicht, minus Befunde */
		double objective() {
			return meanScore + 0.5 * p10Score - HARD_WEIGHT * hardPerRider - WARNING_WEIGHT * warningsPerRider;
		}
	}

	static Evaluation evaluate(PlanParameters parameters, long firstSeed, int riders) {
		return evaluate(parameters, firstSeed, riders, false);
	}

	/** followers = true: Fahrer, die sich an den Plan halten (für die Optimierung), sonst alle Zufallsfahrer */
	static Evaluation evaluate(PlanParameters parameters, long firstSeed, int riders, boolean followers) {
		List<double[]> rows = LongStream.range(firstSeed, firstSeed + riders).parallel().mapToObj(seed -> {
			RandomRider rider = followers ? RandomRider.follower(seed) : RandomRider.of(seed);
			try {
				LoopSimulation.Result result = parameters.apply(() -> LoopSimulation.run(rider.profile(), rider.type()));
				PerformanceModel.Outcome outcome = PerformanceModel.of(seed).evaluate(result.tssByDay(),
						result.firstDay(), result.targetDay());
				return new double[] { outcome.score(), outcome.formAtTarget() - outcome.formAtStart(),
						outcome.overload(), result.hard().size(), result.warnings().size(), 0 };
			} catch (RuntimeException e) {
				return new double[] { 0, 0, 0, 1, 0, 1 };
			}
		}).toList();
		double[] scores = rows.stream().mapToDouble(row -> row[0]).sorted().toArray();
		return new Evaluation(parameters, riders, mean(rows, 0), scores[Math.max(0, scores.length / 10 - 1)],
				mean(rows, 1), mean(rows, 2), mean(rows, 3), mean(rows, 4), (int) rows.stream().filter(r -> r[5] > 0).count());
	}

	private static double mean(List<double[]> rows, int column) {
		return rows.stream().mapToDouble(row -> row[column]).average().orElse(0);
	}

	/** "name=wert,name=wert" auf DEFAULT anwenden */
	static PlanParameters parse(String text) {
		PlanParameters p = PlanParameters.DEFAULT;
		if (text == null || text.isBlank()) {
			return p;
		}
		var components = PlanParameters.class.getRecordComponents();
		Object[] values = Arrays.stream(components).map(c -> {
			try {
				return c.getAccessor().invoke(PlanParameters.DEFAULT);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}).toArray();
		for (String pair : text.split(",")) {
			String[] kv = pair.trim().split("=");
			int index = -1;
			for (int i = 0; i < components.length; i++) {
				if (components[i].getName().equals(kv[0].trim())) {
					index = i;
				}
			}
			if (index < 0) {
				throw new IllegalArgumentException("Unbekannte Stellschraube: " + kv[0]);
			}
			values[index] = components[index].getType() == int.class ? (Object) Integer.parseInt(kv[1].trim())
					: (Object) Double.parseDouble(kv[1].trim());
		}
		try {
			return (PlanParameters) PlanParameters.class.getDeclaredConstructors()[0].newInstance(values);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static String format(Evaluation e) {
		return """
				Stellschrauben: %s
				Fahrer: %d (Abstürze: %d)
				Gesamtwert (höher = besser): %.2f
				  Score im Mittel: %.2f, schwächste 10 %%: %.2f
				  Form-Zuwachs bis zum Ziel-Tag im Mittel: %.2f, Abzug Überlastung: %.2f
				  harte Befunde je Fahrer: %.3f, Warnungen je Fahrer: %.2f
				""".formatted(e.parameters(), e.riders(), e.crashes(), e.objective(), e.meanScore(), e.p10Score(),
				e.meanFormGain(), e.meanOverload(), e.hardPerRider(), e.warningsPerRider());
	}

	public static void main(String[] args) throws IOException {
		PlanParameters parameters = parse(args.length > 0 ? args[0] : "");
		int riders = args.length > 1 && !args[1].isBlank() ? Integer.parseInt(args[1]) : 1000;
		long firstSeed = args.length > 2 && !args[2].isBlank() ? Long.parseLong(args[2]) : 1;
		boolean followers = args.length > 4 && Boolean.parseBoolean(args[4]);
		long started = System.nanoTime();
		Evaluation evaluation = evaluate(parameters, firstSeed, riders, followers);
		String text = format(evaluation) + "Dauer: %.0f s%n".formatted((System.nanoTime() - started) / 1e9);
		Path target = Path.of(args.length > 3 ? args[3] : "build/plan-evaluation.md");
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, text);
		System.out.print(text);
	}
}

package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

/**
 * Zufallssuche nach Schwachstellen: so viele zufällige erfundene Fahrer ({@link RandomRider}) wie in die Zeit passen,
 * jeder im Wochen-Kreislauf ({@link LoopSimulation}). Gesammelt wird nur, was auffällt – je Art die Zahl der
 * betroffenen Fahrer und das kürzeste Beispiel mit Startwert zum Nachstellen. Jede Nacht andere Fahrer (Startwert aus
 * dem Datum). Befunde sind Hinweise, kein Fehler im Lauf: Die feste Simulation bleibt die Prüfung.
 *
 * Aufruf: ./gradlew fuzzSimulation [-PbudgetSeconds=60] [-PfuzzBase=…] → build/fuzz-simulation.md
 *         ./gradlew fuzzSimulation -Pseed=123 → einen Fahrer genau nachstellen (build/fuzz-rider.md)
 */
public final class FuzzSimulation {

	static final int MAX_RIDERS = 100_000;
	static final int BATCH = 400;

	private FuzzSimulation() {
	}

	/** Ein Befund-Art: wie viele Fahrer, wie oft, und das kürzeste Beispiel */
	static final class Finding {
		final boolean hard;
		final AtomicInteger riders = new AtomicInteger();
		final AtomicInteger occurrences = new AtomicInteger();
		volatile long exampleSeed = -1;
		volatile int exampleWeeks = Integer.MAX_VALUE;
		volatile String exampleText = "";
		volatile String exampleRider = "";

		Finding(boolean hard) {
			this.hard = hard;
		}

		synchronized void example(long seed, int weeks, String text, String rider) {
			if (weeks < exampleWeeks || weeks == exampleWeeks && seed < exampleSeed) {
				exampleSeed = seed;
				exampleWeeks = weeks;
				exampleText = text;
				exampleRider = rider;
			}
		}
	}

	public static void main(String[] args) throws IOException {
		Path target = Path.of(args.length > 0 ? args[0] : "build/fuzz-simulation.md");
		long budgetSeconds = args.length > 1 ? Long.parseLong(args[1]) : 60;
		long base = args.length > 2 && !args[2].isBlank() ? Long.parseLong(args[2]) : LocalDate.now().toEpochDay();
		String seed = args.length > 3 ? args[3] : "";
		if (!seed.isBlank()) {
			reproduce(Long.parseLong(seed), target.resolveSibling("fuzz-rider.md"));
			return;
		}

		Map<String, Finding> findings = new ConcurrentHashMap<>();
		AtomicInteger riders = new AtomicInteger();
		AtomicInteger ridersWithWarnings = new AtomicInteger();
		AtomicInteger ridersWithHard = new AtomicInteger();
		long started = System.nanoTime();
		long deadline = started + budgetSeconds * 1_000_000_000L;
		long first = base * 1_000_003L;
		for (long batch = 0; System.nanoTime() < deadline && riders.get() < MAX_RIDERS; batch++) {
			long from = first + batch * BATCH;
			LongStream.range(from, from + BATCH).parallel().forEach(riderSeed -> {
				RandomRider rider = RandomRider.of(riderSeed);
				riders.incrementAndGet();
				try {
					LoopSimulation.Result result = LoopSimulation.run(rider.profile(), rider.type());
					int weeks = result.weeks().size();
					if (!result.hard().isEmpty()) {
						ridersWithHard.incrementAndGet();
					}
					if (!result.warnings().isEmpty()) {
						ridersWithWarnings.incrementAndGet();
					}
					record(findings, true, result.hard(), riderSeed, weeks, rider.description());
					record(findings, false, result.warnings(), riderSeed, weeks, rider.description());
				} catch (RuntimeException | AssertionError | StackOverflowError e) {
					ridersWithHard.incrementAndGet();
					String text = "Absturz: " + e.getClass().getSimpleName() + " " + firstFrame(e);
					record(findings, true, List.of(text + (e.getMessage() == null ? "" : " – " + e.getMessage())),
							riderSeed, 0, rider.description());
				}
			});
		}
		double seconds = (System.nanoTime() - started) / 1e9;
		write(target, findings, riders.get(), ridersWithWarnings.get(), ridersWithHard.get(), base, seconds);
		Files.writeString(Path.of(target.toString().replaceAll("\\.md$", "") + ".tsv"),
				"%s\t%d\t%d\t%d\t%d%n".formatted(LocalDate.ofEpochDay(base), riders.get(), ridersWithHard.get(),
						ridersWithWarnings.get(), findings.size()));
		System.out.printf("Zufallssuche: %d Fahrer in %.0f s, %d mit harten Befunden, %d Arten → %s%n", riders.get(),
				seconds, ridersWithHard.get(), findings.size(), target.toAbsolutePath());
	}

	private static void record(Map<String, Finding> findings, boolean hard, List<String> lines, long seed, int weeks,
			String rider) {
		Map<String, String> firstOfKind = new java.util.LinkedHashMap<>();
		Map<String, Integer> countOfKind = new java.util.HashMap<>();
		for (String line : lines) {
			String kind = (hard ? "HART: " : "") + SimulationReport.kind(line);
			firstOfKind.putIfAbsent(kind, line);
			countOfKind.merge(kind, 1, Integer::sum);
		}
		firstOfKind.forEach((kind, line) -> {
			Finding finding = findings.computeIfAbsent(kind, k -> new Finding(hard));
			finding.riders.incrementAndGet();
			finding.occurrences.addAndGet(countOfKind.get(kind));
			finding.example(seed, weeks, line, rider);
		});
	}

	private static String firstFrame(Throwable e) {
		for (StackTraceElement frame : e.getStackTrace()) {
			if (frame.getClassName().startsWith("io.github.jakobfa")) {
				return "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")";
			}
		}
		return "";
	}

	private static void write(Path target, Map<String, Finding> findings, int riders, int withWarnings, int withHard,
			long base, double seconds) throws IOException {
		StringBuilder out = new StringBuilder("# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)\n\n");
		out.append("%d zufällige Fahrer in %.0f s (Startwert-Basis %d = %s). %d (%.1f %%) mit harten Befunden, %d (%.1f %%) mit Warnungen, %d Arten von Befunden.\n\n"
				.formatted(riders, seconds, base, LocalDate.ofEpochDay(base), withHard, 100.0 * withHard / riders,
						withWarnings, 100.0 * withWarnings / riders, findings.size()));
		out.append("Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.\n\n");
		List<Map.Entry<String, Finding>> sorted = new ArrayList<>(findings.entrySet());
		sorted.sort(Comparator.comparing((Map.Entry<String, Finding> e) -> !e.getValue().hard)
				.thenComparing(e -> -e.getValue().riders.get()));
		out.append("| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |\n|---|---|---|---|---|\n");
		for (var entry : sorted) {
			Finding f = entry.getValue();
			out.append("| %s | %d | %.1f %% | %d | Startwert %d: %s |\n".formatted(entry.getKey().replace("|", "/"),
					f.riders.get(), 100.0 * f.riders.get() / riders, f.occurrences.get(), f.exampleSeed,
					f.exampleText.replace("|", "/")));
		}
		out.append("\n## Beispiel-Fahrer je Art\n\n");
		for (var entry : sorted) {
			out.append("- **").append(entry.getKey().replace("|", "/")).append("** – Startwert ")
					.append(entry.getValue().exampleSeed).append(": ").append(entry.getValue().exampleRider)
					.append('\n');
		}
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
	}

	/** Einen Zufallsfahrer genau nachstellen: Beschreibung, Befunde, Wochenverlauf */
	static void reproduce(long seed, Path target) throws IOException {
		RandomRider rider = RandomRider.of(seed);
		LoopSimulation.Result result = LoopSimulation.run(rider.profile(), rider.type());
		StringBuilder out = new StringBuilder("# Zufallsfahrer " + seed + "\n\n").append(rider.description())
				.append("\n\n");
		result.hard().forEach(line -> out.append("- **HART** ").append(line).append('\n'));
		result.warnings().forEach(line -> out.append("- Warnung: ").append(line).append('\n'));
		out.append("\n| Woche | Phase | geplant h | gefahren h | geplant TSS | gefahren TSS | CTL | Anstieg | tiefster TSB |\n");
		out.append("|---|---|---|---|---|---|---|---|---|\n");
		for (LoopSimulation.Week week : result.weeks()) {
			out.append("| %s | %s | %.1f | %.1f | %d | %d | %.0f | %+.1f | %.0f |\n".formatted(week.monday(),
					week.phase(), week.plannedMinutes() / 60.0, week.actualMinutes() / 60.0, week.plannedTss(),
					week.actualTss(), week.ctl(), week.ctlRamp(), week.minTsb()));
		}
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
		System.out.println(out);
	}
}

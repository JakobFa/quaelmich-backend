package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der nächtliche Bericht: jedes Profil × jeder Fahrer-Typ im Wochen-Kreislauf ({@link LoopSimulation}). Schreibt
 * die Markdown-Datei und daneben eine .tsv mit Zahlen je Lauf; liegt die .tsv der Vornacht vor, steht im Bericht,
 * was sich geändert hat.
 *
 * Aufruf: ./gradlew planSimulation → build/plan-simulation.md (Exit-Code 1 bei harten Verstößen)
 */
public final class SimulationReport {

	private SimulationReport() {
	}

	public static void main(String[] args) throws IOException {
		Path target = Path.of(args.length > 0 ? args[0] : "build/plan-simulation.md");
		Path previous = args.length > 1 ? Path.of(args[1]) : null;

		List<LoopSimulation.Result> results = new ArrayList<>();
		for (RiderType type : RiderType.all()) {
			for (PlanReport.Profile profile : PlanReport.profiles()) {
				results.add(LoopSimulation.run(profile, type));
			}
		}
		long hard = results.stream().mapToLong(result -> result.hard().size()).sum();
		long warnings = results.stream().mapToLong(result -> result.warnings().size()).sum();

		StringBuilder out = new StringBuilder("# Plan-Simulation (erfundene Fahrer im Wochen-Kreislauf)\n\n");
		out.append("Jeder Fahrer (").append(PlanReport.profiles().size()).append(" Profile) wird mit jedem Typ (")
				.append(RiderType.all().size()).append(") kombiniert. Jeden Montag rechnet der Plan neu aus dem, ")
				.append("was gefahren wurde – wie in der App. Zufall je Lauf fest, daher jeden Tag gleich. ")
				.append("CTL = Fitness (42 Tage), ATL = Ermüdung (7 Tage), TSB = CTL − ATL (Frische).\n\n");
		out.append("**Ergebnis: ").append(results.size()).append(" Läufe, ").append(hard).append(" harte Verstöße, ")
				.append(warnings).append(" Warnungen.**\n\n");

		Map<String, String[]> before = previous != null && Files.exists(previous) ? readTsv(previous) : Map.of();
		if (!before.isEmpty()) {
			out.append("## Änderungen gegenüber der Vornacht\n\n");
			List<String> changes = new ArrayList<>();
			for (LoopSimulation.Result result : results) {
				String[] old = before.get(result.key());
				if (old == null) {
					changes.add("- neu: " + result.key());
				} else if (Integer.parseInt(old[0]) != result.hard().size()
						|| Integer.parseInt(old[1]) != result.warnings().size()) {
					changes.add("- %s: hart %s → %d, Warnungen %s → %d".formatted(result.key(), old[0],
							result.hard().size(), old[1], result.warnings().size()));
				}
			}
			out.append(changes.isEmpty() ? "Keine.\n" : String.join("\n", changes) + "\n").append('\n');
		}

		out.append("## Je Fahrer-Typ\n\n| Typ | Läufe | harte Verstöße | Warnungen | häufigste Warnung |\n|---|---|---|---|---|\n");
		for (RiderType type : RiderType.all()) {
			List<LoopSimulation.Result> ofType = results.stream().filter(r -> r.type().equals(type.name())).toList();
			Map<String, Integer> kinds = new LinkedHashMap<>();
			ofType.forEach(r -> r.warnings().forEach(w -> kinds.merge(kind(w), 1, Integer::sum)));
			String top = kinds.entrySet().stream().max(Map.Entry.comparingByValue())
					.map(e -> e.getKey() + " (" + e.getValue() + ")").orElse("–");
			out.append("| %s | %d | %d | %d | %s |\n".formatted(type.name(), ofType.size(),
					ofType.stream().mapToInt(r -> r.hard().size()).sum(),
					ofType.stream().mapToInt(r -> r.warnings().size()).sum(), top));
		}

		out.append("\n## Alle Läufe\n\n| Fahrer | Typ | Wochen | Erholungswochen | max. Belastungswochen am Stück | ")
				.append("max. CTL-Anstieg | tiefster TSB | TSB am Event | hart | Warnungen |\n")
				.append("|---|---|---|---|---|---|---|---|---|---|\n");
		for (LoopSimulation.Result result : results) {
			double maxRamp = result.weeks().stream().skip(2).mapToDouble(LoopSimulation.Week::ctlRamp).max().orElse(0);
			double minTsb = result.weeks().stream().mapToDouble(LoopSimulation.Week::minTsb).min().orElse(0);
			out.append("| %s | %s | %d | %d | %d | %.1f | %.0f | %s | %d | %d |\n".formatted(result.profile(),
					result.type(), result.weeks().size(), result.recoveryWeeks(), result.maxLoadWeeksInRow(), maxRamp,
					minTsb, Double.isNaN(result.eventDayTsb()) ? "–" : "%.0f".formatted(result.eventDayTsb()),
					result.hard().size(), result.warnings().size()));
		}

		out.append("\n## Befunde je Lauf\n");
		for (LoopSimulation.Result result : results) {
			if (result.hard().isEmpty() && result.warnings().isEmpty()) {
				continue;
			}
			out.append("\n### ").append(result.key()).append("\n\n");
			result.hard().forEach(line -> out.append("- **HART** ").append(line).append('\n'));
			result.warnings().forEach(line -> out.append("- Warnung: ").append(line).append('\n'));
		}

		out.append("\n## Wochenverlauf (planmäßige Fahrer und Läufe mit harten Verstößen)\n");
		for (LoopSimulation.Result result : results) {
			// lokal alle Tabellen: SIM_ALL_TABLES=1 ./gradlew planSimulation
			boolean all = System.getenv("SIM_ALL_TABLES") != null;
			if (!all && !result.type().equals(RiderType.baseline().name()) && result.hard().isEmpty()) {
				continue;
			}
			out.append("\n### ").append(result.key()).append("\n\n");
			out.append("| Woche | Phase | geplant h | gefahren h | geplant TSS | gefahren TSS | CTL | Anstieg | tiefster TSB |\n");
			out.append("|---|---|---|---|---|---|---|---|---|\n");
			for (LoopSimulation.Week week : result.weeks()) {
				out.append("| %s | %s | %.1f | %.1f | %d | %d | %.0f | %+.1f | %.0f |\n".formatted(week.monday(),
						week.phase(), week.plannedMinutes() / 60.0, week.actualMinutes() / 60.0, week.plannedTss(),
						week.actualTss(), week.ctl(), week.ctlRamp(), week.minTsb()));
			}
		}

		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
		StringBuilder tsv = new StringBuilder("lauf\thart\twarnungen\n");
		results.forEach(r -> tsv.append(r.key()).append('\t').append(r.hard().size()).append('\t')
				.append(r.warnings().size()).append('\n'));
		Files.writeString(Path.of(target.toString().replaceAll("\\.md$", "") + ".tsv"), tsv);
		System.out.println("Plan-Simulation: " + target.toAbsolutePath() + " (" + results.size() + " Läufe, " + hard
				+ " harte Verstöße, " + warnings + " Warnungen)");
		if (hard > 0) {
			System.exit(1);
		}
	}

	/** Art einer Warnung ohne Datum und Zahlen, zum Zählen */
	static String kind(String warning) {
		return warning.replaceAll("^(Woche ab )?\\d{4}-\\d{2}-\\d{2}:?\\s*", "").replaceAll("\"[^\"]*\"", "…")
				.replaceAll("\\(.*?\\)", "").replaceAll("[-+]?\\d+([.,]\\d+)?", "#").replaceAll("\\s+", " ").trim();
	}

	private static Map<String, String[]> readTsv(Path path) throws IOException {
		Map<String, String[]> map = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path).stream().skip(1).toList()) {
			String[] parts = line.split("\t");
			if (parts.length == 3) {
				map.put(parts[0], new String[] { parts[1], parts[2] });
			}
		}
		return map;
	}
}

package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Simuliert Fahrer über ihren ganzen Plan: Jeder erfundene Fahrer fährt die geplanten Einheiten mit realistischen
 * Abweichungen (mal 20 % zu wenig, mal zu hart, ausgefallene Tage, eine Krankheit) und der Plan passt sich wie in
 * der App an. Daraus entsteht der Belastungsverlauf (CTL/ATL/TSB), der gegen sportliche Grenzen geprüft wird.
 * Der Zufall ist je Fahrer festgelegt: Derselbe Code ergibt jeden Tag dieselbe Simulation.
 *
 * Aufruf: ./gradlew planSimulation → build/plan-simulation.md (Exit-Code 1 bei harten Verstößen)
 */
public final class PlanSimulation {

	/** Harte Grenzen: Verstöße lassen den Lauf scheitern */
	static final int HARD_MIN_TSB = -60;
	/** Warnungen: erscheinen im Bericht, scheitern nicht */
	static final double WARN_WEEKLY_CTL_RAMP = 8;
	static final int WARN_TSB = -35;
	static final int ILLNESS_DAYS_MIN = 3;
	static final int ILLNESS_DAYS_MAX = 6;

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanAdaptationService ADAPTATION = new PlanAdaptationService(LIBRARY);

	private PlanSimulation() {
	}

	record Week(LocalDate monday, String phase, int plannedMinutes, int actualMinutes, int plannedTss, int actualTss,
			double ctl, double ctlRamp, double minTsb) {
	}

	record Result(String name, List<Week> weeks, List<String> hard, List<String> warnings, int missed,
			int adapted, double eventDayTsb) {
	}

	public static void main(String[] args) throws IOException {
		StringBuilder out = new StringBuilder("# Plan-Simulation (erfundene Fahrer mit realistischen Abweichungen)\n\n");
		out.append("Jeder Fahrer fährt seinen Plan mit Abweichungen (Seed je Fahrer, daher jeden Tag gleich). ")
				.append("CTL = Fitness (42 Tage), ATL = Ermüdung (7 Tage), TSB = CTL − ATL (Frische).\n\n");
		List<Result> results = PlanReport.profiles().stream().map(PlanSimulation::simulate).toList();

		long hard = results.stream().mapToLong(result -> result.hard().size()).sum();
		long warnings = results.stream().mapToLong(result -> result.warnings().size()).sum();
		out.append("**Ergebnis: ").append(hard).append(" harte Verstöße, ").append(warnings).append(" Warnungen.**\n\n");
		out.append("| Fahrer | Wochen | max. CTL-Anstieg/Woche | tiefster TSB | TSB am Event | ausgefallen | angepasst |\n");
		out.append("|---|---|---|---|---|---|---|\n");
		for (Result result : results) {
			double maxRamp = result.weeks().stream().skip(2).mapToDouble(Week::ctlRamp).max().orElse(0);
			double minTsb = result.weeks().stream().mapToDouble(Week::minTsb).min().orElse(0);
			out.append("| %s | %d | %.1f | %.0f | %s | %d | %d |\n".formatted(result.name(),
					result.weeks().size(), maxRamp, minTsb,
					Double.isNaN(result.eventDayTsb()) ? "–" : "%.0f".formatted(result.eventDayTsb()), result.missed(),
					result.adapted()));
		}
		for (Result result : results) {
			if (result.hard().isEmpty() && result.warnings().isEmpty()) {
				continue;
			}
			out.append("\n## ").append(result.name()).append("\n\n");
			result.hard().forEach(line -> out.append("- **HART** ").append(line).append('\n'));
			result.warnings().forEach(line -> out.append("- Warnung: ").append(line).append('\n'));
		}
		out.append("\n## Wochenverlauf je Fahrer\n");
		for (Result result : results) {
			out.append("\n### ").append(result.name()).append("\n\n");
			out.append("| Woche | Phase | geplant h | gefahren h | geplant TSS | gefahren TSS | CTL | Anstieg | tiefster TSB |\n");
			out.append("|---|---|---|---|---|---|---|---|---|\n");
			for (Week week : result.weeks()) {
				out.append("| %s | %s | %.1f | %.1f | %d | %d | %.0f | %+.1f | %.0f |\n".formatted(week.monday(),
						week.phase(), week.plannedMinutes() / 60.0, week.actualMinutes() / 60.0, week.plannedTss(),
						week.actualTss(), week.ctl(), week.ctlRamp(), week.minTsb()));
			}
		}
		Path target = Path.of(args.length > 0 ? args[0] : "build/plan-simulation.md");
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
		System.out.println("Plan-Simulation: " + target.toAbsolutePath() + " (" + hard + " harte Verstöße, "
				+ warnings + " Warnungen)");
		if (hard > 0) {
			System.exit(1);
		}
	}

	/** Fitness, mit der ein erfundener Fahrer startet (frisch, TSB 0): je höher das Level, desto mehr */
	static double startFitness(io.github.jakobfa.webtechproject.model.Level level) {
		return switch (level) {
			case BEGINNER -> 15;
			case HOBBY -> 25;
			case AMBITIOUS -> 40;
			case PERFORMANCE -> 70;
		};
	}

	static Result simulate(PlanReport.Profile profile) {
		Athlete athlete = PlanReport.athleteOf(profile);
		double ctl = startFitness(profile.level());
		return simulate(profile.name(), athlete, PlanReport.planOf(athlete), ctl, ctl);
	}

	/** Ein Fahrer mit seinem Plan und seiner Startform (CTL/ATL am Tag vor dem ersten Plantag) */
	static Result simulate(String name, Athlete athlete, List<PlannedWorkout> planWorkouts, double ctl0, double atl0) {
		return simulate(name, athlete, planWorkouts, ctl0, atl0, Map.of());
	}

	/** Wie oben, mit abweichender Zeit in einzelnen Wochen (Montag → Verfügbarkeit dieser Woche) */
	static Result simulate(String name, Athlete athlete, List<PlannedWorkout> planWorkouts, double ctl0, double atl0,
			Map<LocalDate, io.github.jakobfa.webtechproject.model.WeeklyAvailability> weekAvailability) {
		List<PlannedWorkout> plan = new ArrayList<>(planWorkouts);
		plan.sort(java.util.Comparator.comparing(PlannedWorkout::getDate));
		// derselbe Fahrer erlebt dieselben Abweichungen, egal welchen Plan er fährt: so sind Pläne vergleichbar
		Random random = new Random(java.util.Objects.hash(athlete.getLevel(), athlete.getAvailability().totalHours(),
				athlete.getFtp(), athlete.getEvent() == null ? 0 : athlete.getEvent().getName()));

		List<String> hard = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		LocalDate first = plan.getFirst().getDate();
		LocalDate last = plan.getLast().getDate();

		Map<LocalDate, double[]> weeklyAcc = new TreeMap<>(); // planMin, actMin, planTss, actTss, minTsb
		Map<LocalDate, String> phases = new TreeMap<>();
		Map<LocalDate, Double> ctlAtWeekEnd = new TreeMap<>();
		double ctl = ctl0;
		double atl = atl0;
		int missed = 0;
		int adapted = 0;
		int illnessLeft = 0;
		boolean illnessJustEnded = false;
		LocalDate lastMissed = null;
		double eventDayTsb = Double.NaN;
		LocalDate eventDate = athlete.getEvent() == null ? null : athlete.getEvent().getDate();

		for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
			LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			double[] acc = weeklyAcc.computeIfAbsent(monday, key -> new double[] {0, 0, 0, 0, 999});
			final LocalDate today = day;
			PlannedWorkout planned = plan.stream().filter(candidate -> candidate.getDate().equals(today)).findFirst()
					.orElse(null);
			double tssToday = 0;
			if (illnessLeft == 0 && random.nextDouble() < 0.004) {
				illnessLeft = ILLNESS_DAYS_MIN + random.nextInt(ILLNESS_DAYS_MAX - ILLNESS_DAYS_MIN + 1);
			}
			if (planned != null) {
				phases.putIfAbsent(monday, planned.getPhase().name());
				Workout workout = LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
				acc[0] += workout.getDurationInMinutes();
				acc[2] += workout.getTss();
				int availableMinutes = (int) (weekAvailability.getOrDefault(monday, athlete.getAvailability())
						.hoursOn(day.getDayOfWeek()) * 60);
				if (workout.getDurationInMinutes() > availableMinutes) {
					hard.add("%s: %d min geplant, aber nur %d min Zeit%s".formatted(day, workout.getDurationInMinutes(),
							availableMinutes, planned.getAdjustmentNote() != null ? " (nach Anpassung: " + workout.getTitle() + ")" : " (" + workout.getTitle() + ")"));
				}
				if (illnessLeft > 0 || random.nextDouble() < 0.07) {
					missed++;
					lastMissed = day;
				} else {
					double compliance = Math.clamp(0.97 + 0.12 * random.nextGaussian(), 0.6, 1.25);
					int minutes = (int) Math.round(workout.getDurationInMinutes() * compliance);
					int tss = (int) Math.round(workout.getTss() * compliance * (0.9 + 0.25 * random.nextDouble()));
					planned.setCompleted(true);
					planned.setActivityId("sim-" + day);
					planned.setActualDurationMinutes(minutes);
					planned.setActualTss(tss);
					tssToday = tss;
					acc[1] += minutes;
					acc[3] += tss;
					final LocalDate rideDay = day;
					List<PlannedWorkout> upcoming = plan.stream().filter(next -> next.getDate().isAfter(rideDay))
							.limit(8).toList();
					adapted += ADAPTATION.adaptAfter(planned, upcoming).isPresent() ? 1 : 0;
				}
			}
			if (illnessLeft > 0) {
				illnessLeft--;
				illnessJustEnded = illnessLeft == 0;
			} else if (illnessJustEnded) {
				illnessJustEnded = false;
			}
			if (illnessJustEnded && lastMissed != null) {
				final LocalDate lastDay = day;
				List<PlannedWorkout> upcoming = plan.stream().filter(next -> next.getDate().isAfter(lastDay))
						.limit(8).toList();
				adapted += ADAPTATION.adaptAfterAbsence(lastDay, "Pause", upcoming).isPresent() ? 1 : 0;
			}
			double tsb = ctl - atl; // Frische am Morgen, vor der heutigen Fahrt
			acc[4] = Math.min(acc[4], tsb);
			if (eventDate != null && day.equals(eventDate)) {
				eventDayTsb = tsb;
			}
			ctl += (tssToday - ctl) / 42.0;
			atl += (tssToday - atl) / 7.0;
			ctlAtWeekEnd.put(monday, ctl);
		}

		List<Week> weeks = new ArrayList<>();
		double previousCtl = ctl0;
		boolean firstWeek = true;
		for (var entry : weeklyAcc.entrySet()) {
			LocalDate monday = entry.getKey();
			double[] acc = entry.getValue();
			double weekCtl = ctlAtWeekEnd.get(monday);
			double ramp = weekCtl - previousCtl;
			weeks.add(new Week(monday, phases.getOrDefault(monday, "?"), (int) acc[0], (int) acc[1], (int) acc[2],
					(int) acc[3], weekCtl, ramp, acc[4] > 900 ? 0 : acc[4]));
			if (!firstWeek && weeks.size() > 2 && ramp > WARN_WEEKLY_CTL_RAMP) {
				warnings.add("Woche ab %s: Fitness steigt um %.1f CTL (Grenze %.0f)".formatted(monday, ramp,
						WARN_WEEKLY_CTL_RAMP));
			}
			if (acc[4] < HARD_MIN_TSB) {
				hard.add("Woche ab %s: Frische bis %.0f TSB gefallen (Grenze %d)".formatted(monday, acc[4],
						HARD_MIN_TSB));
			} else if (acc[4] < WARN_TSB) {
				warnings.add("Woche ab %s: Frische bis %.0f TSB gefallen (Warnung ab %d)".formatted(monday, acc[4],
						WARN_TSB));
			}
			previousCtl = weekCtl;
			firstWeek = false;
		}
		return new Result(name, weeks, hard, warnings, missed, adapted, eventDayTsb);
	}
}

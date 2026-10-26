package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.TrainingGoal;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Bericht für die wöchentliche Plan-Prüfung (Routine "Trainingsplan-Check"): erzeugt komplette Pläne für viele
 * erfundene Fahrer und schreibt je Woche die Kennzahlen, die ein Trainer anschauen würde. Bewusst ohne echte
 * Nutzerdaten – die echten Pläne entstehen mit genau diesem Generator.
 *
 * Aufruf: ./gradlew planReport → build/plan-report.md
 */
public final class PlanReport {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // ein Montag
	private static final Set<WorkoutType> EASY = Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE,
			WorkoutType.LONG_RIDE);

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	private PlanReport() {
	}

	record Profile(String name, Level level, TrainingGoal goal, EventType type, EventGoal eventGoal, double km,
			int meters, int weeks, double[] hours) {
	}

	private static Profile event(String name, Level level, EventType type, EventGoal goal, double km, int meters,
			int weeks, double... hours) {
		return new Profile(name, level, TrainingGoal.EVENT, type, goal, km, meters, weeks, hours);
	}

	private static Profile open(String name, Level level, TrainingGoal goal, double... hours) {
		return new Profile(name, level, goal, null, null, 0, 0, 0, hours);
	}

	/** Breite Mischung: alle Level, alle Event-Arten, alle Ziele ohne Event, wenig bis viel Zeit (Mo–So) */
	static List<Profile> profiles() {
		return List.of(
				event("Einsteigerin, 3 h, erste 100 km", Level.BEGINNER, EventType.GRAN_FONDO, EventGoal.FINISH, 100,
						800, 12, 0, 1, 0, 0, 0, 2, 0),
				event("Einsteiger, 5 h, Gravel ankommen", Level.BEGINNER, EventType.GRAVEL, EventGoal.FINISH, 80,
						900, 14, 0, 1, 0, 1, 0, 2, 1),
				event("Hobby, 6 h, Gran Fondo", Level.HOBBY, EventType.GRAN_FONDO, EventGoal.GOOD_TIME, 160, 2000, 16,
						0, 1, 0, 1.5, 0, 2, 1.5),
				event("Hobby, 7 h, Zeitfahren", Level.HOBBY, EventType.TIME_TRIAL, EventGoal.GOOD_TIME, 40, 300, 8, 0,
						1.5, 0, 1.5, 1, 2, 1),
				event("Hobby, 5 h, nur am Wochenende, Bikepacking", Level.HOBBY, EventType.BIKEPACKING,
						EventGoal.FINISH, 400, 4000, 20, 0, 0, 0, 0, 0, 3, 2),
				event("Ambitioniert, 7 h, Ultra ankommen", Level.AMBITIOUS, EventType.ULTRA, EventGoal.FINISH, 600,
						6000, 24, 0, 1, 1, 1, 0, 2.5, 1.5),
				event("Ambitioniert, 10 h, Ötztaler vorne", Level.AMBITIOUS, EventType.GRAN_FONDO, EventGoal.COMPETE,
						227, 5500, 20, 0, 1.5, 1, 1.5, 0, 4, 2),
				event("Ambitioniert, 8 h, Straßenrennen", Level.AMBITIOUS, EventType.ROAD_RACE, EventGoal.GOOD_TIME,
						120, 1500, 14, 0, 1.5, 1, 1.5, 0, 2.5, 1.5),
				event("Ambitioniert, 4 h, wenig Zeit, Gran Fondo", Level.AMBITIOUS, EventType.GRAN_FONDO,
						EventGoal.GOOD_TIME, 150, 2500, 12, 0, 1, 0, 1, 0, 2, 0),
				event("Leistung, 12 h, Kriterium", Level.PERFORMANCE, EventType.CRITERIUM, EventGoal.COMPETE, 50, 200,
						10, 1, 1.5, 2, 1.5, 0, 3, 3),
				event("Leistung, 15 h, Ultra auf Zeit", Level.PERFORMANCE, EventType.ULTRA, EventGoal.COMPETE, 1000,
						10000, 26, 1, 2, 1.5, 2, 0, 5, 3.5),
				event("Leistung, 9 h, Gravel vorne, kurzfristig", Level.PERFORMANCE, EventType.GRAVEL,
						EventGoal.COMPETE, 120, 1800, 5, 0, 1.5, 1.5, 1.5, 0, 3, 1.5),
				open("Hobby, 5 h, FTP steigern", Level.HOBBY, TrainingGoal.FTP, 0, 1, 0, 1, 0, 2, 1),
				open("Ambitioniert, 8 h, FTP steigern", Level.AMBITIOUS, TrainingGoal.FTP, 0, 1.5, 1, 1.5, 0, 2.5,
						1.5),
				open("Hobby, 6 h, Ausdauer", Level.HOBBY, TrainingGoal.ENDURANCE, 0, 1, 0, 1, 0, 2.5, 1.5),
				open("Einsteiger, 3 h, fit bleiben", Level.BEGINNER, TrainingGoal.FITNESS, 0, 1, 0, 1, 0, 1, 0),
				open("Ambitioniert, 6 h, fit bleiben", Level.AMBITIOUS, TrainingGoal.FITNESS, 0, 1, 1, 1, 0, 2, 1),
				open("Ambitioniert, 7 h, schneller am Berg", Level.AMBITIOUS, TrainingGoal.CLIMBING, 0, 1.5, 0, 1.5, 0,
						2.5, 1.5));
	}

	static Athlete athleteOf(Profile profile) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(profile.hours()[0]);
		availability.setTuesday(profile.hours()[1]);
		availability.setWednesday(profile.hours()[2]);
		availability.setThursday(profile.hours()[3]);
		availability.setFriday(profile.hours()[4]);
		availability.setSaturday(profile.hours()[5]);
		availability.setSunday(profile.hours()[6]);

		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setLevel(profile.level());
		athlete.setAvailability(availability);
		athlete.setTrainingGoal(profile.goal());
		if (profile.goal() == TrainingGoal.EVENT) {
			Event event = new Event();
			event.setName(profile.name());
			event.setDate(START.plusWeeks(profile.weeks()).with(DayOfWeek.SATURDAY));
			event.setDistanceKm(profile.km());
			event.setElevationMeters(profile.meters());
			event.setType(profile.type());
			event.setGoal(profile.eventGoal());
			athlete.setEvent(event);
		}
		return athlete;
	}

	static List<PlannedWorkout> planOf(Athlete athlete) {
		return GENERATOR.generateSeason(athlete, START, Map.of(), List.of());
	}

	public static void main(String[] args) throws IOException {
		StringBuilder out = new StringBuilder();
		out.append("# Plan-Bericht (erfundene Profile, FTP 250 W, Start ").append(START).append(")\n\n");
		out.append("Spalten je Woche: Phase · Stunden · TSS · Minuten in Zone (≤ 75 % / 76–94 % / ≥ 95 % FTP) · ")
				.append("längste Fahrt · harte Einheiten (TSS).\n\n");
		for (Profile profile : profiles()) {
			Athlete athlete = athleteOf(profile);
			out.append(report(profile, planOf(athlete)));
		}
		Path target = Path.of(args.length > 0 ? args[0] : "build/plan-report.md");
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.writeString(target, out);
		System.out.println("Plan-Bericht: " + target.toAbsolutePath());
	}

	static String report(Profile profile, List<PlannedWorkout> plan) {
		StringBuilder out = new StringBuilder("## ").append(profile.name()).append("\n\n");
		out.append("| Woche | Phase | h | TSS | Zonen min (locker/mittel/hart) | längste | harte Einheiten |\n");
		out.append("|---|---|---|---|---|---|---|\n");
		Map<LocalDate, List<PlannedWorkout>> weeks = new TreeMap<>();
		for (PlannedWorkout planned : plan) {
			weeks.computeIfAbsent(planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
					monday -> new ArrayList<>()).add(planned);
		}
		int number = 1;
		int[] total = new int[3];
		for (List<PlannedWorkout> week : weeks.values()) {
			int seconds = 0;
			int tss = 0;
			int longest = 0;
			int[] zones = new int[3];
			List<String> hard = new ArrayList<>();
			for (PlannedWorkout planned : week) {
				Workout workout = LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
				tss += workout.getTss();
				longest = Math.max(longest, workout.getDurationInMinutes());
				for (WorkoutStep step : workout.getSteps()) {
					seconds += step.durationSeconds();
					int percent = (step.powerStartPercent() + step.powerEndPercent()) / 2;
					zones[percent <= 75 ? 0 : percent < 95 ? 1 : 2] += step.durationSeconds();
				}
				if (!EASY.contains(workout.getType())) {
					hard.add("%s %s (%d)".formatted(planned.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN),
							workout.getTitle(), workout.getTss()));
				}
			}
			for (int i = 0; i < 3; i++) {
				total[i] += zones[i];
			}
			out.append("| %d | %s | %.1f | %d | %d / %d / %d | %d min | %s |\n".formatted(number++,
					week.getFirst().getPhase(), seconds / 3600.0, tss, zones[0] / 60, zones[1] / 60, zones[2] / 60,
					longest, String.join(", ", hard)));
		}
		int all = Math.max(1, total[0] + total[1] + total[2]);
		out.append("\nVerteilung über den ganzen Plan: %d %% locker, %d %% mittel, %d %% hart\n\n".formatted(
				Math.round(100.0 * total[0] / all), Math.round(100.0 * total[1] / all),
				Math.round(100.0 * total[2] / all)));
		return out.toString();
	}

}

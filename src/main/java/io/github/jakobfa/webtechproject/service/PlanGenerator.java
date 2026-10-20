package io.github.jakobfa.webtechproject.service;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Erzeugt einen Trainingsplan vom Startdatum bis zum Event.
 *
 * Regeln (vereinfachte Periodisierung):
 * - Phasen nach Wochen bis zum Event: Grundlage → Aufbau → Spitze → Tapering,
 *   in Grundlage und Aufbau ist jede 4. Woche eine Erholungswoche.
 * - Trainiert wird nur an Tagen mit verfügbarer Zeit, nie am Event-Tag und am Tag davor.
 * - Der Tag mit der meisten Zeit wird die lange Ausfahrt.
 * - Harte Einheiten je nach Level und Phase, nie an zwei Tagen hintereinander
 *   und nie direkt vor oder nach der langen Ausfahrt.
 * - Harte Workouts werden innerhalb einer Phase alle zwei Wochen eine Stufe schwerer.
 * - Alle anderen Tage werden Grundlage bzw. Regeneration, so lang wie Zeit vorhanden ist.
 */
@Component
public class PlanGenerator {

	static final int MAX_WEEKS = 52;

	private static final Map<Level, Integer> HARD_SESSIONS_PER_LEVEL = Map.of(
			Level.BEGINNER, 1,
			Level.HOBBY, 2,
			Level.AMBITIOUS, 2,
			Level.PERFORMANCE, 3);

	private final WorkoutLibrary library;

	public PlanGenerator(WorkoutLibrary library) {
		this.library = library;
	}

	public List<PlannedWorkout> generate(Athlete athlete, LocalDate startDate) {
		LocalDate eventDate = athlete.getEvent().getDate();
		LocalDate lastTrainingDay = eventDate.minusDays(2);
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		LocalDate eventMonday = eventDate.with(previousOrSame(DayOfWeek.MONDAY));

		long weeks = Math.min(ChronoUnit.WEEKS.between(firstMonday, eventMonday) + 1, MAX_WEEKS);

		List<PlannedWorkout> plan = new ArrayList<>();
		// Zählt die Wochen je Phase, damit die harten Workouts innerhalb einer Phase schwerer werden
		Map<Phase, Integer> weeksInPhase = new EnumMap<>(Phase.class);
		for (int weekIndex = 0; weekIndex < weeks; weekIndex++) {
			LocalDate monday = firstMonday.plusWeeks(weekIndex);
			long weeksUntilEvent = ChronoUnit.WEEKS.between(monday, eventMonday);
			Phase phase = phaseFor(weekIndex, weeksUntilEvent);

			List<LocalDate> trainingDays = monday.datesUntil(monday.plusWeeks(1))
					.filter(day -> !day.isBefore(startDate) && !day.isAfter(lastTrainingDay))
					.filter(day -> availableMinutes(athlete, day) > 0)
					.toList();

			int step = progressionStep(athlete.getLevel(), phase, weeksInPhase.getOrDefault(phase, 0));
			weeksInPhase.merge(phase, 1, Integer::sum);

			plan.addAll(planWeek(athlete, trainingDays, phase, weekIndex, weeks, weeksUntilEvent, step));
		}
		return plan;
	}

	static Phase phaseFor(int weekIndex, long weeksUntilEvent) {
		if (weeksUntilEvent <= 1) {
			return Phase.TAPER;
		}
		if (weeksUntilEvent <= 4) {
			return Phase.PEAK;
		}
		Phase phase = weeksUntilEvent <= 10 ? Phase.BUILD : Phase.BASE;
		return weekIndex % 4 == 3 ? Phase.RECOVERY : phase;
	}

	/**
	 * Steigerungsstufe der harten Workouts: alle zwei Wochen einer Phase eine Stufe schwerer.
	 * Die Spitze startet auf mittlerer Stufe, im Tapering bleibt es moderat.
	 * Einsteiger und Hobbyfahrer bleiben auf den leichteren Stufen.
	 */
	static int progressionStep(Level level, Phase phase, int weekInPhase) {
		int step = switch (phase) {
			case PEAK -> 1 + weekInPhase / 2;
			case TAPER -> 1;
			default -> weekInPhase / 2;
		};
		int maxStep = switch (level) {
			case BEGINNER -> 1;
			case HOBBY -> 2;
			default -> 3;
		};
		return Math.min(step, maxStep);
	}

	private List<PlannedWorkout> planWeek(Athlete athlete, List<LocalDate> days, Phase phase, int weekIndex,
			long totalWeeks, long weeksUntilEvent, int step) {
		if (days.isEmpty()) {
			return List.of();
		}
		double volume = volumeFactor(phase, weeksUntilEvent);

		// Tag mit der meisten Zeit wird die lange Ausfahrt (bei Gleichstand der spätere, meist Wochenende)
		LocalDate longDay = days.stream()
				.max(Comparator.comparingInt((LocalDate day) -> availableMinutes(athlete, day))
						.thenComparing(Comparator.naturalOrder()))
				.orElseThrow();

		// Harte Einheiten nicht am Tag der langen Ausfahrt und nicht direkt davor oder danach
		List<LocalDate> candidates = days.stream()
				.filter(day -> Math.abs(ChronoUnit.DAYS.between(longDay, day)) > 1)
				.sorted(Comparator.comparingInt((LocalDate day) -> availableMinutes(athlete, day)).reversed())
				.toList();

		List<PlannedWorkout> week = new ArrayList<>();
		List<LocalDate> hardDays = new ArrayList<>();

		for (WorkoutType type : hardWorkoutsFor(athlete.getLevel(), phase, weekIndex, totalWeeks, weeksUntilEvent)) {
			Workout workout = library.variant(type, step);
			candidates.stream()
					.filter(day -> !hardDays.contains(day))
					.filter(day -> hardDays.stream().noneMatch(hard -> Math.abs(ChronoUnit.DAYS.between(hard, day)) == 1))
					.filter(day -> availableMinutes(athlete, day) >= workout.getDurationInMinutes())
					.findFirst()
					.ifPresent(day -> {
						hardDays.add(day);
						week.add(new PlannedWorkout(day, workout, null, phase));
					});
		}

		for (LocalDate day : days) {
			if (hardDays.contains(day)) {
				continue;
			}
			int minutes = floorToQuarterHour(availableMinutes(athlete, day) * volume);
			boolean isLongDay = day.equals(longDay) && phase != Phase.TAPER && phase != Phase.RECOVERY;
			week.add(easyWorkout(day, minutes, isLongDay, phase));
		}

		week.sort(Comparator.comparing(PlannedWorkout::getDate));
		return week;
	}

	private PlannedWorkout easyWorkout(LocalDate day, int minutes, boolean isLongDay, Phase phase) {
		if (isLongDay && minutes >= 150) {
			int capped = Math.min(minutes, 360);
			return new PlannedWorkout(day, WorkoutLibrary.longRide(capped), capped, phase);
		}
		if (minutes >= 60) {
			int capped = Math.min(minutes, 300);
			return new PlannedWorkout(day, WorkoutLibrary.endurance(capped), capped, phase);
		}
		int atLeast = Math.max(minutes, 15);
		return new PlannedWorkout(day, WorkoutLibrary.recovery(atLeast), atLeast, phase);
	}

	static List<WorkoutType> hardWorkoutsFor(Level level, Phase phase, int weekIndex, long totalWeeks,
			long weeksUntilEvent) {
		if (phase == Phase.RECOVERY) {
			return List.of();
		}
		if (phase == Phase.TAPER) {
			// Event-Woche: nur kurze Sprints zum "Öffnen" der Beine, Woche davor eine kurze harte Einheit
			return List.of(weeksUntilEvent == 0 ? WorkoutType.SPRINT : WorkoutType.VO2MAX);
		}

		List<WorkoutType> pool = poolFor(level, phase);
		int count = HARD_SESSIONS_PER_LEVEL.get(level);

		List<WorkoutType> result = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			result.add(pool.get((weekIndex + i) % pool.size()));
		}
		// Zu Beginn eines längeren Plans einmal die FTP bestimmen
		if (weekIndex == 0 && totalWeeks >= 6) {
			result.set(0, WorkoutType.FTP_TEST);
		}
		return result;
	}

	private static List<WorkoutType> poolFor(Level level, Phase phase) {
		boolean beginner = level == Level.BEGINNER;
		return switch (phase) {
			case BASE -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.TEMPO);
			case BUILD -> beginner
					? List.of(WorkoutType.SWEET_SPOT, WorkoutType.THRESHOLD)
					: List.of(WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER, WorkoutType.SWEET_SPOT);
			case PEAK -> beginner
					? List.of(WorkoutType.THRESHOLD, WorkoutType.VO2MAX)
					: List.of(WorkoutType.VO2MAX, WorkoutType.THRESHOLD, WorkoutType.ANAEROBIC);
			default -> List.of(WorkoutType.SWEET_SPOT);
		};
	}

	private static double volumeFactor(Phase phase, long weeksUntilEvent) {
		return switch (phase) {
			case RECOVERY -> 0.6;
			case TAPER -> weeksUntilEvent == 0 ? 0.5 : 0.7;
			default -> 1.0;
		};
	}

	private static int availableMinutes(Athlete athlete, LocalDate day) {
		return (int) Math.round(athlete.getAvailability().hoursOn(day.getDayOfWeek()) * 60);
	}

	/** Abrunden, damit nie mehr Zeit geplant wird, als verfügbar ist */
	private static int floorToQuarterHour(double minutes) {
		return (int) (Math.floor(minutes / 15) * 15);
	}

}

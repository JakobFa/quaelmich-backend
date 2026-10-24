package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/**
 * Fortschritt für die Kopfzeile im Plan: Erfahrungspunkte und Level, Serie und Stand im Plan.
 *
 * Erfahrungspunkte = gefahrene Trainingslast (TSS), auch Pendel- und Zusatzfahrten.
 * Ohne gemessenen TSS zählt ein abgehaktes Workout mit seinem geplanten TSS, eine Fahrt ohne Daten
 * mit einer ruhigen Schätzung aus der Dauer.
 */
@Service
public class ProgressService {

	/** Ein Level: Nummer, Name und ab wie vielen Punkten es gilt */
	public record Level(int number, String name, int fromXp) {
	}

	/** Stand im Plan: welche Woche, wie viel vom Fälligen erledigt, wie lange bis zum Event */
	public record PlanProgress(int week, int totalWeeks, int completed, int due, long daysToEvent,
			String eventName) {
	}

	/** nextLevel ist null, wenn es kein benanntes Level mehr darüber gibt; load null ohne Plan */
	public record Progress(int xp, Level level, Level nextLevel, int streakWeeks, PlanProgress plan,
			LoadCheck load) {
	}

	/** Wie gut passt die Belastung der letzten 7 Tage zum Plan? */
	public enum LoadStatus {
		TOO_LITTLE, ON_TRACK, A_BIT_MUCH, TOO_MUCH, RESTING
	}

	/** Geplante und tatsächliche Belastung (TSS) der letzten 7 Tage inklusive heute */
	public record LoadCheck(int plannedTss, int actualTss, LoadStatus status) {
	}

	static final int LOAD_DAYS = 7;
	/** unter 70 % des Geplanten = zu wenig, bis 115 % = auf Kurs, bis 140 % = etwas viel, darüber zu viel */
	static final double TOO_LITTLE_BELOW = 0.7;
	static final double ON_TRACK_UP_TO = 1.15;
	static final double A_BIT_MUCH_UP_TO = 1.4;

	/** Level-Namen und Schwellen – die Abstände wachsen, damit es später nicht zu schnell geht */
	static final List<Level> LEVELS = List.of(
			new Level(1, "Kettenfett-Neuling", 0),
			new Level(2, "Schlauchflicker", 250),
			new Level(3, "Pendel-Punk", 600),
			new Level(4, "Windschatten-Schnorrer", 1100),
			new Level(5, "Kurbel-Krawallo", 1800),
			new Level(6, "Asphalt-Anarchist", 2700),
			new Level(7, "Bergziegen-Punk", 3800),
			new Level(8, "Laktat-Rebell", 5200),
			new Level(9, "Kopfsteinpflaster-Kaiser", 7000),
			new Level(10, "Serpentinen-Schreck", 9200),
			new Level(11, "Watt-Vandale", 12000),
			new Level(12, "Quälmeister", 15000));

	/** Danach geht es alle 4000 Punkte als "Quälmeister II, III …" weiter */
	static final int XP_PER_MASTER_LEVEL = 4000;

	/** Eine Woche zählt für die Serie, wenn mindestens 80 % ihrer Workouts erledigt sind */
	static final double STREAK_SHARE = 0.8;

	/** Grobe Schätzung für Fahrten ohne TSS: lockeres Tempo ≈ 30 TSS pro Stunde */
	static final double XP_PER_MINUTE_WITHOUT_TSS = 0.5;

	private final AthleteService athleteService;
	private final TrainingPlanRepository planRepository;
	private final ExtraRideRepository extraRideRepository;
	private final WorkoutLibrary library;
	private final Clock clock;

	public ProgressService(AthleteService athleteService, TrainingPlanRepository planRepository,
			ExtraRideRepository extraRideRepository, WorkoutLibrary library, Clock clock) {
		this.athleteService = athleteService;
		this.planRepository = planRepository;
		this.extraRideRepository = extraRideRepository;
		this.library = library;
		this.clock = clock;
	}

	/** Leer, solange es kein Profil gibt */
	@Transactional(readOnly = true)
	public Optional<Progress> progressOf(Long userId) {
		return athleteService.findByOwner(userId).map(athlete -> {
			Optional<TrainingPlan> plan = planRepository.findByAthleteId(athlete.getId());
			List<PlannedWorkout> workouts = plan.map(TrainingPlan::getWorkouts).orElse(List.of());
			List<ExtraRide> extras = extraRideRepository.findByAthleteIdAndDateGreaterThanEqualOrderByDate(
					athlete.getId(), LocalDate.of(2000, 1, 1));

			int xp = workouts.stream().mapToInt(this::xpOf).sum()
					+ extras.stream().mapToInt(ProgressService::xpOf).sum();
			LocalDate today = LocalDate.now(clock);
			return new Progress(xp, levelFor(xp), nextLevel(xp), streakWeeks(workouts, today),
					plan.map(p -> planProgress(p, athlete, today)).orElse(null),
					plan.map(p -> loadCheck(workouts, extras, today)).orElse(null));
		});
	}

	int xpOf(PlannedWorkout planned) {
		if (planned.getActualTss() != null) {
			return planned.getActualTss();
		}
		if (planned.getActivityId() != null && planned.getActualDurationMinutes() != null) {
			return estimate(planned.getActualDurationMinutes());
		}
		if (planned.isCompleted()) {
			return library.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
					.map(workout -> workout.getTss()).orElse(0);
		}
		return 0;
	}

	static int xpOf(ExtraRide ride) {
		if (ride.getTss() != null) {
			return ride.getTss();
		}
		return ride.getDurationMinutes() == null ? 0 : estimate(ride.getDurationMinutes());
	}

	private static int estimate(int minutes) {
		return (int) Math.round(minutes * XP_PER_MINUTE_WITHOUT_TSS);
	}

	static Level levelFor(int xp) {
		Level last = LEVELS.getLast();
		if (xp < last.fromXp() + XP_PER_MASTER_LEVEL) {
			return LEVELS.stream().filter(level -> level.fromXp() <= xp).reduce((a, b) -> b).orElseThrow();
		}
		return masterLevel((xp - last.fromXp()) / XP_PER_MASTER_LEVEL);
	}

	static Level nextLevel(int xp) {
		return LEVELS.stream().filter(level -> level.fromXp() > xp).findFirst()
				.orElseGet(() -> masterLevel((xp - LEVELS.getLast().fromXp()) / XP_PER_MASTER_LEVEL + 1));
	}

	/** Quälmeister II, III, … ab 19000, 23000, … Punkten */
	private static Level masterLevel(int rank) {
		Level last = LEVELS.getLast();
		if (rank == 0) {
			return last;
		}
		return new Level(last.number() + rank, last.name() + " " + roman(rank + 1),
				last.fromXp() + rank * XP_PER_MASTER_LEVEL);
	}

	private static String roman(int number) {
		String[] numerals = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
		int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (number >= values[i]) {
				result.append(numerals[i]);
				number -= values[i];
			}
		}
		return result.toString();
	}

	/**
	 * Wochen in Folge mit mindestens 80 % erledigten Workouts, rückwärts ab der letzten abgeschlossenen Woche.
	 * Die laufende Woche zählt mit, sobald sie die 80 % schon erreicht hat. Wochen ohne Workouts überspringen.
	 */
	static int streakWeeks(List<PlannedWorkout> workouts, LocalDate today) {
		Map<LocalDate, List<PlannedWorkout>> byWeek = workouts.stream().collect(Collectors.groupingBy(
				planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
				TreeMap::new, Collectors.toList()));
		LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

		int streak = 0;
		for (LocalDate monday : ((TreeMap<LocalDate, List<PlannedWorkout>>) byWeek).descendingKeySet()) {
			if (monday.isAfter(currentWeek)) {
				continue;
			}
			boolean strong = isStrongWeek(byWeek.get(monday));
			if (monday.equals(currentWeek)) {
				if (strong) {
					streak++;
				}
				continue; // eine laufende Woche bricht die Serie noch nicht
			}
			if (!strong) {
				break;
			}
			streak++;
		}
		return streak;
	}

	private static boolean isStrongWeek(List<PlannedWorkout> week) {
		long done = week.stream().filter(PlannedWorkout::isCompleted).count();
		return done >= Math.ceil(week.size() * STREAK_SHARE);
	}

	/**
	 * Belastung der letzten 7 Tage: geplant ist, was bis heute fällig war (ohne Pausen); gefahren sind die
	 * zugeordneten Fahrten, abgehakte Workouts ohne Fahrt mit ihrem geplanten TSS und alle Zusatzeinheiten.
	 */
	LoadCheck loadCheck(List<PlannedWorkout> workouts, List<ExtraRide> extras, LocalDate today) {
		LocalDate from = today.minusDays(LOAD_DAYS - 1L);
		int planned = 0;
		int actual = 0;
		for (PlannedWorkout workout : workouts) {
			boolean inWindow = !workout.getDate().isBefore(from) && !workout.getDate().isAfter(today);
			boolean due = workout.getDate().isBefore(today) || workout.isCompleted() || workout.getActualTss() != null;
			if (!inWindow || !due || workout.getSkipReason() == SkipReason.PAUSE) {
				continue;
			}
			int plannedTss = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes())
					.map(w -> w.getTss()).orElse(0);
			planned += plannedTss;
			if (workout.getActualTss() != null) {
				actual += workout.getActualTss();
			} else if (workout.isCompleted()) {
				actual += plannedTss;
			}
		}
		for (ExtraRide ride : extras) {
			if (!ride.getDate().isBefore(from) && !ride.getDate().isAfter(today)) {
				actual += xpOf(ride);
			}
		}
		return new LoadCheck(planned, actual, statusOf(planned, actual));
	}

	static LoadStatus statusOf(int planned, int actual) {
		if (planned == 0 && actual == 0) {
			return LoadStatus.RESTING;
		}
		double ratio = (double) actual / Math.max(planned, 30);
		if (ratio < TOO_LITTLE_BELOW) {
			return LoadStatus.TOO_LITTLE;
		}
		if (ratio <= ON_TRACK_UP_TO) {
			return LoadStatus.ON_TRACK;
		}
		return ratio <= A_BIT_MUCH_UP_TO ? LoadStatus.A_BIT_MUCH : LoadStatus.TOO_MUCH;
	}

	static PlanProgress planProgress(TrainingPlan plan, Athlete athlete, LocalDate today) {
		LocalDate firstMonday = plan.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate eventDate = athlete.getEvent().getDate();
		LocalDate eventMonday = eventDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		int totalWeeks = (int) ChronoUnit.WEEKS.between(firstMonday, eventMonday) + 1;
		int week = (int) Math.clamp(ChronoUnit.WEEKS.between(firstMonday, today) + 1, 1, totalWeeks);

		// fällig = alles vor heute, dazu heute schon Erledigtes
		List<PlannedWorkout> due = plan.getWorkouts().stream()
				.filter(planned -> planned.getDate().isBefore(today)
						|| (planned.getDate().equals(today) && planned.isCompleted()))
				.filter(planned -> planned.getSkipReason() != SkipReason.PAUSE) // Pause zählt nicht als verpasst
				.toList();
		int completed = (int) due.stream().filter(PlannedWorkout::isCompleted).count();
		return new PlanProgress(week, totalWeeks, completed, due.size(),
				Math.max(0, ChronoUnit.DAYS.between(today, eventDate)), athlete.getEvent().getName());
	}

}

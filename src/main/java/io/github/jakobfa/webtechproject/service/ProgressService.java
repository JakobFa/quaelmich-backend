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
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.Event;
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

	/**
	 * Stand im Plan: welche Woche, wie viel vom Fälligen erledigt, wie lange bis zum Event.
	 * Bei einem Ziel ohne Event: cycle = Nummer des 8-Wochen-Zyklus, week/totalWeeks die Woche darin, und der
	 * Countdown läuft bis zum nächsten FTP-Test (eventName "FTP-Test"); bei Event-Plänen ist cycle null.
	 */
	public record PlanProgress(int week, int totalWeeks, int completed, int due, long daysToEvent,
			String eventName, Integer cycle) {

		public PlanProgress(int week, int totalWeeks, int completed, int due, long daysToEvent, String eventName) {
			this(week, totalWeeks, completed, due, daysToEvent, eventName, null);
		}
	}

	/** nextLevel ist null, wenn es kein benanntes Level mehr darüber gibt; load null ohne Plan */
	public record Progress(int xp, Level level, Level nextLevel, int streakWeeks, PlanProgress plan,
			LoadCheck load) {
	}

	/** Wie gut passt die Belastung der letzten 7 Tage zum Plan? */
	public enum LoadStatus {
		TOO_LITTLE, ON_TRACK, A_BIT_MUCH, TOO_MUCH, RESTING
	}

	/**
	 * Belastung der letzten 7 Tage: geplant (was laut Plan fällig war), gefahren (tatsächlich) und der
	 * chronische Wochenschnitt, an dem "zu viel" gemessen wird.
	 */
	public record LoadCheck(int plannedTss, int actualTss, int chronicTss, LoadStatus status) {
	}

	static final int LOAD_DAYS = 7;
	static final int CHRONIC_DAYS = 28;
	/** "Zu wenig" misst am Plan: unter 70 % des Geplanten bist du hinter deinem Plan zurück */
	static final double TOO_LITTLE_BELOW = 0.7;
	/**
	 * "Zu viel" misst nicht am Plan, sondern am Acute:Chronic-Verhältnis (ACWR): die Belastung der letzten
	 * 7 Tage im Vergleich zu dem, was du in den letzten ~4 Wochen im Schnitt gemacht hast. Nur ein echter
	 * Sprung gegenüber dem Gewohnten ist riskant – eine lockere Mehrfahrt in einer knapp geplanten Woche nicht.
	 * Richtwerte aus der Sportwissenschaft: über 1,3 etwas viel, über 1,5 deutlich erhöhtes Risiko.
	 */
	static final double A_BIT_MUCH_ACWR = 1.3;
	static final double TOO_MUCH_ACWR = 1.5;
	/** So wenig Wochenbelastung nehmen wir mindestens als Basis an – sonst würde jede kleine Fahrt als Sprung gelten */
	static final int MIN_CHRONIC_WEEKLY = 150;

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
	private io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents;

	/** weitere Events – optional, damit Tests ohne Datenbank auskommen */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setRaceEvents(io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents) {
		this.raceEvents = raceEvents;
	}

	private List<Event> moreEventsOf(Athlete athlete) {
		return raceEvents == null ? List.of()
				: raceEvents.findByAthleteIdOrderByDate(athlete.getId()).stream()
						.map(io.github.jakobfa.webtechproject.model.RaceEvent::toEvent).toList();
	}

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
					plan.map(p -> planProgress(p, athlete, moreEventsOf(athlete), today)).orElse(null),
					plan.map(p -> loadCheck(p, workouts, extras, today)).orElse(null));
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
	LoadCheck loadCheck(TrainingPlan plan, List<PlannedWorkout> workouts, List<ExtraRide> extras, LocalDate today) {
		LocalDate from = today.minusDays(LOAD_DAYS - 1L);
		int planned = plannedLoad(workouts, from, today, true);
		int actual = doneLoad(workouts, extras, from, today);
		int chronic = chronicWeekly(plan, workouts, extras, today);
		return new LoadCheck(planned, actual, chronic, statusOf(planned, actual, chronic));
	}

	/**
	 * Chronischer Wochenschnitt: die tatsächliche Belastung der letzten 28 Tage, auf eine Woche gerechnet.
	 * Für die Tage vor dem Trainingsstart (noch keine Historie) nehmen wir an, dass die geplante Belastung
	 * der kommenden Wochen gefahren worden wäre – so wird der Schnitt beim Start nicht künstlich niedrig
	 * (sonst sähe jede erste Woche wie ein großer Sprung aus). Nie unter {@link #MIN_CHRONIC_WEEKLY}.
	 */
	int chronicWeekly(TrainingPlan plan, List<PlannedWorkout> workouts, List<ExtraRide> extras, LocalDate today) {
		LocalDate chronicFrom = today.minusDays(CHRONIC_DAYS - 1L);
		int done = doneLoad(workouts, extras, chronicFrom, today);
		LocalDate planStart = plan.getStartDate();
		long missingDays = Math.clamp(ChronoUnit.DAYS.between(chronicFrom, planStart), 0, CHRONIC_DAYS);
		double intendedWeekly = intendedWeekly(workouts, today);
		double seeded = done + intendedWeekly / 7.0 * missingDays;
		return Math.max(MIN_CHRONIC_WEEKLY, (int) Math.round(seeded / (CHRONIC_DAYS / 7.0)));
	}

	/** Geplante Wochenbelastung, an der sich der Trainierende orientiert: Schnitt der kommenden 4 Wochen */
	private double intendedWeekly(List<PlannedWorkout> workouts, LocalDate today) {
		int forward = rawPlannedLoad(workouts, today, today.plusDays(CHRONIC_DAYS - 1L));
		if (forward > 0) {
			return forward / (CHRONIC_DAYS / 7.0);
		}
		// Plan läuft aus: dann der Schnitt der letzten 4 Wochen
		return rawPlannedLoad(workouts, today.minusDays(CHRONIC_DAYS - 1L), today) / (CHRONIC_DAYS / 7.0);
	}

	/** Tatsächlich gefahrene Belastung im Zeitraum: zugeordnete Fahrten, abgehakte Workouts, Zusatzfahrten */
	private int doneLoad(List<PlannedWorkout> workouts, List<ExtraRide> extras, LocalDate from, LocalDate to) {
		int sum = 0;
		for (PlannedWorkout workout : workouts) {
			if (inWindow(workout.getDate(), from, to) && workout.getSkipReason() != SkipReason.PAUSE) {
				sum += xpOf(workout);
			}
		}
		for (ExtraRide ride : extras) {
			if (inWindow(ride.getDate(), from, to)) {
				sum += xpOf(ride);
			}
		}
		return sum;
	}

	/** Geplante Belastung; dueOnly = nur was bis heute fällig war (für den Vergleich "hinter dem Plan") */
	private int plannedLoad(List<PlannedWorkout> workouts, LocalDate from, LocalDate to, boolean dueOnly) {
		int sum = 0;
		for (PlannedWorkout workout : workouts) {
			if (!inWindow(workout.getDate(), from, to) || workout.getSkipReason() == SkipReason.PAUSE) {
				continue;
			}
			boolean due = workout.getDate().isBefore(to) || workout.isCompleted() || workout.getActualTss() != null;
			if (dueOnly && !due) {
				continue;
			}
			sum += library.resolve(workout.getWorkoutId(), workout.getDurationMinutes()).map(Workout::getTss).orElse(0);
		}
		return sum;
	}

	private int rawPlannedLoad(List<PlannedWorkout> workouts, LocalDate from, LocalDate to) {
		return plannedLoad(workouts, from, to, false);
	}

	private static boolean inWindow(LocalDate date, LocalDate from, LocalDate to) {
		return !date.isBefore(from) && !date.isAfter(to);
	}

	/**
	 * "Zu wenig" am Plan gemessen (hast du Einheiten ausgelassen?), "zu viel" am chronischen Schnitt (ACWR,
	 * echte Überlastung). So gilt eine lockere Mehrfahrt in einer knapp geplanten Woche nicht als zu viel.
	 */
	static LoadStatus statusOf(int planned, int actual, int chronicWeekly) {
		if (planned == 0 && actual == 0) {
			return LoadStatus.RESTING;
		}
		double acwr = (double) actual / Math.max(chronicWeekly, MIN_CHRONIC_WEEKLY);
		if (acwr > TOO_MUCH_ACWR) {
			return LoadStatus.TOO_MUCH;
		}
		if (acwr > A_BIT_MUCH_ACWR) {
			return LoadStatus.A_BIT_MUCH;
		}
		double planRatio = (double) actual / Math.max(planned, 30);
		return planRatio < TOO_LITTLE_BELOW ? LoadStatus.TOO_LITTLE : LoadStatus.ON_TRACK;
	}

	static PlanProgress planProgress(TrainingPlan plan, Athlete athlete, LocalDate today) {
		return planProgress(plan, athlete, List.of(), today);
	}

	/** Countdown bis zum nächsten anstehenden Event; Wochen bis zum letzten Event der Saison */
	static PlanProgress planProgress(TrainingPlan plan, Athlete athlete, List<Event> more, LocalDate today) {
		LocalDate firstMonday = plan.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		if (!athlete.hasEventGoal()) {
			return openProgress(plan, firstMonday, today);
		}
		Event next = EventProfile.nextEvent(athlete.getEvent(), more, today);
		LocalDate eventDate = next.getDate();
		LocalDate lastEvent = more.stream().map(Event::getDate).filter(d -> d.isAfter(athlete.getEvent().getDate()))
				.max(LocalDate::compareTo).orElse(athlete.getEvent().getDate());
		LocalDate eventMonday = lastEvent.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		int totalWeeks = (int) ChronoUnit.WEEKS.between(firstMonday, eventMonday) + 1;
		int week = (int) Math.clamp(ChronoUnit.WEEKS.between(firstMonday, today) + 1, 1, totalWeeks);

		List<PlannedWorkout> due = dueOf(plan, today);
		return new PlanProgress(week, totalWeeks, completedOf(due), due.size(),
				Math.max(0, ChronoUnit.DAYS.between(today, eventDate)), next.getName());
	}

	/** Ziel ohne Event: Woche im 8-Wochen-Zyklus und Tage bis zum nächsten FTP-Test (sonst bis Zyklusende) */
	private static PlanProgress openProgress(TrainingPlan plan, LocalDate firstMonday, LocalDate today) {
		int cycleWeeks = PlanGenerator.OPEN_CYCLE.size();
		int weeksSinceStart = (int) Math.max(0, ChronoUnit.WEEKS.between(firstMonday, today));
		LocalDate cycleEnd = firstMonday.plusWeeks((weeksSinceStart / cycleWeeks + 1L) * cycleWeeks);
		LocalDate nextTest = plan.getWorkouts().stream()
				.filter(planned -> planned.getWorkoutId() != null && planned.getWorkoutId() == WorkoutLibrary.FTP_TEST)
				.filter(planned -> !planned.getDate().isBefore(today) && !planned.isCompleted())
				.map(PlannedWorkout::getDate).min(LocalDate::compareTo).orElse(null);
		List<PlannedWorkout> due = dueOf(plan, today);
		return new PlanProgress(weeksSinceStart % cycleWeeks + 1, cycleWeeks, completedOf(due), due.size(),
				ChronoUnit.DAYS.between(today, nextTest != null ? nextTest : cycleEnd),
				nextTest != null ? "FTP-Test" : "Ende des Zyklus", weeksSinceStart / cycleWeeks + 1);
	}

	/** fällig = alles vor heute, dazu heute schon Erledigtes; Pause zählt nicht als verpasst */
	private static List<PlannedWorkout> dueOf(TrainingPlan plan, LocalDate today) {
		return plan.getWorkouts().stream()
				.filter(planned -> planned.getDate().isBefore(today)
						|| (planned.getDate().equals(today) && planned.isCompleted()))
				.filter(planned -> planned.getSkipReason() != SkipReason.PAUSE)
				.toList();
	}

	private static int completedOf(List<PlannedWorkout> due) {
		return (int) due.stream().filter(PlannedWorkout::isCompleted).count();
	}

}

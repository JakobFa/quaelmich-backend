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
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.EventProfile.Focus;

/**
 * Erzeugt einen Trainingsplan vom Startdatum bis zum Event – angepasst an das Ziel.
 *
 * Regeln (angelehnt an gängige Trainingslehre, z. B. Friel, Coggan, Seiler):
 * - Phasen rückwärts vom Event: Tapering (Event-Woche + 1), Spitze (2–3 Wochen), Aufbau (ca. 40 % der
 *   übrigen Zeit), davor Grundlage. Nach 3 Belastungswochen folgt eine Erholungswoche – nie direkt vor der Spitze.
 * - Der Umfang steigt: Woche 1 nutzt ca. 70 % der verfügbaren Zeit, dann 5 % mehr pro Belastungswoche.
 * - Die lange Ausfahrt wächst aufs Ziel hin (bei langen Events von ca. 40 % auf 75 % der Event-Dauer, max. 6 h),
 *   bei langen Events im Aufbau mit Tempo-Blöcken im Renntempo.
 * - Die harten Workouts richten sich nach dem Schwerpunkt des Events (siehe {@link EventProfile})
 *   und nach der Wochenzeit: wenig Zeit → Sweet-Spot-betont, viel Zeit → polarisiert.
 * - Harte Einheiten nie an zwei Tagen hintereinander und nie direkt vor oder nach der langen Ausfahrt.
 * - FTP-Test am Anfang und zu Beginn des Aufbaus.
 * - Event-Woche: eine kurze, knackige Einheit, zwei Tage vorher Ruhe, am Vortag ein kurzer Opener.
 */
@Component
public class PlanGenerator {

	static final int MAX_WEEKS = 52;

	/** Umfang in Woche 1 und Steigerung pro Belastungswoche */
	static final double START_VOLUME = 0.7;
	static final double VOLUME_STEP = 0.05;

	/** Lange Ausfahrt: höchstens 6 h, mindestens 1:30 h */
	static final int MAX_LONG_RIDE = 360;
	static final int MIN_LONG_RIDE = 90;

	/** Unter dieser Wochenzeit Sweet-Spot-betont, darüber polarisiert */
	static final double FEW_HOURS = 6;
	static final double MANY_HOURS = 10;

	/** Frühestens so viele Wochen nach dem letzten FTP-Test kommt der nächste */
	static final int FTP_TEST_EVERY_WEEKS = 6;

	/** Kurze Alternativen (je ≤ 1 h), wenn die geplante harte Einheit an keinem Tag Platz hat */
	private static final List<WorkoutType> SHORT_FALLBACKS = List.of(WorkoutType.TEMPO, WorkoutType.VO2MAX);

	private static final Set<WorkoutType> TOO_HARD_FOR_BEGINNERS = Set.of(WorkoutType.ANAEROBIC,
			WorkoutType.SPRINT, WorkoutType.OVER_UNDER);

	private final WorkoutLibrary library;

	public PlanGenerator(WorkoutLibrary library) {
		this.library = library;
	}

	public List<PlannedWorkout> generate(Athlete athlete, LocalDate startDate) {
		return generate(athlete, startDate, Map.of());
	}

	/** Nach einem Event: so viele Tage ohne harte Einheit (stattdessen locker ausrollen) */
	static final int RECOVERY_DAYS_AFTER_EVENT = 3;

	/**
	 * Saison mit mehreren gleich wichtigen Events: Abschnitt für Abschnitt auf das jeweils nächste hin –
	 * bis zum ersten wie immer, danach jeweils vom Tag nach dem Event an, mit ein paar lockeren Tagen zur
	 * Erholung. Ohne weitere Events genau derselbe Plan wie {@link #generate(Athlete, LocalDate, Map)}.
	 */
	public List<PlannedWorkout> generateSeason(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, List<Event> moreEvents) {
		return generateSeason(athlete, startDate, weekAvailability, moreEvents,
				startDate.plusWeeks(OPEN_HORIZON_WEEKS));
	}

	/**
	 * Wie oben; horizon = bis wann ein Plan ohne Event (Ziel ohne Termin) geplant wird. Bei Event-Zielen
	 * spielt er keine Rolle – dort endet der Plan mit dem Event.
	 */
	public List<PlannedWorkout> generateSeason(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, List<Event> moreEvents, LocalDate horizon) {
		return athlete.hasEventGoal()
				? ridesOfSeason(athlete, startDate, weekAvailability, moreEvents)
				: generateOpenEnded(athlete, startDate, weekAvailability, horizon);
	}

	private List<PlannedWorkout> ridesOfSeason(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, List<Event> moreEvents) {
		if (moreEvents.isEmpty()) {
			return generate(athlete, startDate, weekAvailability);
		}
		List<Event> events = new ArrayList<>(moreEvents);
		events.add(athlete.getEvent());
		events.sort(Comparator.comparing(Event::getDate));

		List<PlannedWorkout> season = new ArrayList<>();
		LocalDate segmentStart = startDate;
		boolean afterEvent = false;
		for (Event event : events) {
			if (!event.getDate().isAfter(segmentStart)) {
				continue;
			}
			List<PlannedWorkout> segment = generate(withEvent(athlete, event), segmentStart, weekAvailability);
			if (afterEvent) {
				segment = recoverAfterEvent(segment, segmentStart);
			}
			season.addAll(segment);
			segmentStart = event.getDate().plusDays(1);
			afterEvent = true;
		}
		return season;
	}

	/** Gleicher Fahrer, anderes Ziel-Event (nur was der Generator braucht) */
	private static Athlete withEvent(Athlete athlete, Event event) {
		Athlete target = new Athlete();
		target.setLevel(athlete.getLevel());
		target.setAvailability(athlete.getAvailability());
		target.setFtp(athlete.getFtp());
		target.setEvent(event);
		return target;
	}

	/** Die ersten Tage nach einem Event: alles wird kurzes, lockeres Ausrollen (auch Grundlage und FTP-Test) */
	private List<PlannedWorkout> recoverAfterEvent(List<PlannedWorkout> segment, LocalDate firstDay) {
		LocalDate until = firstDay.plusDays(RECOVERY_DAYS_AFTER_EVENT - 1L);
		return segment.stream().map(planned -> {
			Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
			boolean shortRecovery = workout.getType() == WorkoutType.RECOVERY && workout.getDurationInMinutes() <= 45;
			if (planned.getDate().isAfter(until) || shortRecovery) {
				return planned;
			}
			int minutes = Math.max(30, Math.min(45, workout.getDurationInMinutes()));
			return new PlannedWorkout(planned.getDate(), WorkoutLibrary.recovery(minutes), minutes,
					planned.getPhase());
		}).toList();
	}

	/**
	 * Wie oben, aber mit abweichender Zeit für einzelne Wochen ("diese Woche habe ich weniger Zeit").
	 * Schlüssel ist der Montag der Woche.
	 */
	public List<PlannedWorkout> generate(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability) {
		LocalDate eventDate = athlete.getEvent().getDate();
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		LocalDate eventMonday = eventDate.with(previousOrSame(DayOfWeek.MONDAY));
		int allWeeks = (int) ChronoUnit.WEEKS.between(firstMonday, eventMonday) + 1;
		if (allWeeks < 1) {
			return List.of();
		}
		List<Phase> phases = phasesFor(allWeeks);
		int loadWeeksTotal = (int) phases.stream().filter(PlanGenerator::isLoadPhase).count();
		// Bei sehr weit entfernten Events nur das erste Jahr planen
		return planWeeks(athlete, EventProfile.of(athlete), phases, startDate, eventDate, weekAvailability,
				Math.min(allWeeks, MAX_WEEKS), loadWeeksTotal);
	}

	// ---------- Ziele ohne Event ----------

	/** Ein Plan ohne Event wird so weit im Voraus geplant (und beim Öffnen verlängert) */
	public static final int OPEN_HORIZON_WEEKS = 12;
	/** Ein Zyklus: 3 Wochen Grundlage, Erholung, 3 Wochen Aufbau, Erholung – danach von vorn, eine Stufe höher */
	static final List<Phase> OPEN_CYCLE = List.of(Phase.BASE, Phase.BASE, Phase.BASE, Phase.RECOVERY,
			Phase.BUILD, Phase.BUILD, Phase.BUILD, Phase.RECOVERY);
	/** Nach so vielen Belastungswochen ist die lange Ausfahrt auf voller Länge */
	static final int OPEN_LOAD_WEEKS_TO_FULL = 12;

	/**
	 * Plan für ein Ziel ohne Termin (FTP, Ausdauer, fit bleiben, Berg): wiederkehrende 8-Wochen-Zyklen bis zum
	 * Horizont. FTP-Test zu Beginn und nach jedem Zyklus (direkt nach der Erholungswoche); die harten Einheiten
	 * werden von Zyklus zu Zyklus schwerer, bis zur Obergrenze des Levels.
	 */
	public List<PlannedWorkout> generateOpenEnded(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, LocalDate horizon) {
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		int weeks = (int) ChronoUnit.WEEKS.between(firstMonday, horizon.with(previousOrSame(DayOfWeek.MONDAY)))
				+ 1;
		if (weeks < 1) {
			return List.of();
		}
		return planWeeks(athlete, EventProfile.of(athlete), openPhases(weeks), startDate, null, weekAvailability,
				weeks, OPEN_LOAD_WEEKS_TO_FULL);
	}

	static List<Phase> openPhases(int weeks) {
		List<Phase> phases = new ArrayList<>();
		for (int i = 0; i < weeks; i++) {
			phases.add(OPEN_CYCLE.get(i % OPEN_CYCLE.size()));
		}
		return phases;
	}

	/**
	 * Die Wochen nacheinander planen. eventDate null = Plan ohne Event: kein Tapering, keine Event-Woche,
	 * und vom Ende her gilt nichts als "kurz vor dem Event".
	 */
	private List<PlannedWorkout> planWeeks(Athlete athlete, EventProfile profile, List<Phase> phases,
			LocalDate startDate, LocalDate eventDate, Map<LocalDate, WeeklyAvailability> weekAvailability,
			int weeks, int loadWeeksTotal) {
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		int allWeeks = eventDate == null ? Integer.MAX_VALUE : phases.size();
		List<PlannedWorkout> plan = new ArrayList<>();
		Map<Phase, Integer> weeksInPhase = new EnumMap<>(Phase.class);
		int loadWeek = 0;
		int lastFtpTest = -FTP_TEST_EVERY_WEEKS;
		for (int weekIndex = 0; weekIndex < weeks; weekIndex++) {
			LocalDate monday = firstMonday.plusWeeks(weekIndex);
			WeeklyAvailability availability = weekAvailability.getOrDefault(monday, athlete.getAvailability());
			double weeklyHours = availability.totalHours();
			Phase phase = phases.get(weekIndex);
			int weeksUntilEvent = eventDate == null ? Integer.MAX_VALUE : allWeeks - 1 - weekIndex;

			List<LocalDate> trainingDays = monday.datesUntil(monday.plusWeeks(1))
					.filter(day -> !day.isBefore(startDate) && (eventDate == null || day.isBefore(eventDate)))
					.filter(day -> availableMinutes(availability, day) > 0)
					.toList();

			if (weeksUntilEvent == 0) {
				plan.addAll(planEventWeek(availability, trainingDays, eventDate, profile));
				continue;
			}

			boolean ftpTest = isFtpTestWeek(phases, weekIndex, allWeeks, weeksUntilEvent, lastFtpTest);
			if (ftpTest) {
				lastFtpTest = weekIndex;
			}

			int step = progressionStep(athlete.getLevel(), phase, weeksInPhase.getOrDefault(phase, 0),
					profile.goal());
			weeksInPhase.merge(phase, 1, Integer::sum);

			WeekPlan week = new WeekPlan(phase, weekIndex, step,
					volumeFactor(phase, loadWeek),
					longRideMinutes(profile, phase, loadWeek, loadWeeksTotal),
					useTempoLongRide(profile, phase, loadWeek),
					hardWorkoutsFor(athlete.getLevel(), profile.focus(), phase, weekIndex, weeksUntilEvent,
							weeklyHours, ftpTest,
							profile.goal()));
			plan.addAll(planWeek(availability, trainingDays, week));

			if (isLoadPhase(phase)) {
				loadWeek++;
			}
		}
		return plan;
	}

	/** Was in einer normalen Woche geplant wird */
	record WeekPlan(Phase phase, int weekIndex, int step, double volume, int longRideMinutes,
			boolean tempoLongRide, List<WorkoutType> hardWorkouts) {
	}

	// ---------- Phasen ----------

	/**
	 * Phasen rückwärts vom Event: zuletzt Tapering (Event-Woche + eine Woche), davor Spitze, davor Aufbau
	 * (ca. 40 % der übrigen Wochen), ganz vorn Grundlage. In Grundlage und Aufbau folgt auf 3 Belastungswochen
	 * eine Erholungswoche – außer direkt vor der Spitze.
	 */
	static List<Phase> phasesFor(int weeks) {
		int taper = weeks >= 3 ? 2 : 1;
		int peak = Math.min(weeks >= 16 ? 3 : weeks >= 6 ? 2 : 1, weeks - taper);
		int rest = weeks - taper - peak;
		int build = (int) Math.round(rest * 0.4);
		int base = rest - build;

		List<Phase> phases = new ArrayList<>();
		for (int i = 0; i < weeks; i++) {
			phases.add(i < base ? Phase.BASE : i < base + build ? Phase.BUILD : i < rest + peak ? Phase.PEAK
					: Phase.TAPER);
		}
		int loaded = 0;
		for (int i = 0; i < rest; i++) {
			boolean lastBeforePeak = i == rest - 1 && peak > 0;
			if (loaded == 3 && !lastBeforePeak) {
				phases.set(i, Phase.RECOVERY);
				loaded = 0;
			} else {
				loaded++;
			}
		}
		return phases;
	}

	private static boolean isLoadPhase(Phase phase) {
		return phase == Phase.BASE || phase == Phase.BUILD || phase == Phase.PEAK;
	}

	/** Erste Belastungswoche im Aufbau – dort wird die FTP neu getestet; -1, wenn es keinen Aufbau gibt */
	/**
	 * FTP-Test zu Beginn (bei Plänen ab 6 Wochen) und danach alle 6–8 Wochen – immer direkt nach einer
	 * Erholungswoche, weil man dann frisch ist. In den letzten 3 Wochen vor dem Event nicht mehr.
	 */
	static boolean isFtpTestWeek(List<Phase> phases, int weekIndex, int allWeeks, int weeksUntilEvent,
			int lastFtpTest) {
		if (weekIndex == 0) {
			return allWeeks >= 6;
		}
		boolean afterRecovery = phases.get(weekIndex - 1) == Phase.RECOVERY && isLoadPhase(phases.get(weekIndex));
		return afterRecovery && weekIndex - lastFtpTest >= FTP_TEST_EVERY_WEEKS && weeksUntilEvent >= 3;
	}

	/**
	 * Steigerungsstufe der harten Workouts: alle zwei Wochen einer Phase eine Stufe schwerer.
	 * Die Spitze startet auf mittlerer Stufe, im Tapering bleibt es moderat.
	 * Einsteiger und Hobbyfahrer bleiben auf den leichteren Stufen.
	 */
	static int progressionStep(Level level, Phase phase, int weekInPhase) {
		return progressionStep(level, phase, weekInPhase, EventGoal.GOOD_TIME);
	}

	/** Vorne mitfahren: eine Stufe schwerer (auch über die Level-Grenze), ankommen: eine leichter */
	static int progressionStep(Level level, Phase phase, int weekInPhase, EventGoal goal) {
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
		return switch (goal) {
			case COMPETE -> Math.min(step + 1, Math.min(maxStep + 1, 3));
			case FINISH -> Math.max(0, Math.min(step, maxStep) - 1);
			case GOOD_TIME -> Math.min(step, maxStep);
		};
	}

	// ---------- Umfang ----------

	/** Anteil der verfügbaren Zeit, der für lockere Fahrten genutzt wird */
	static double volumeFactor(Phase phase, int loadWeek) {
		double ramp = Math.min(1.0, START_VOLUME + VOLUME_STEP * loadWeek);
		return switch (phase) {
			case RECOVERY -> 0.6 * ramp; // Erholung: deutlich weniger als die Wochen davor
			case TAPER -> 0.6;
			// Spitze: weiter steigern statt sprunghaft auf 100 % – sonst +30 % von einer Woche zur nächsten
			case PEAK -> Math.min(1.0, ramp + VOLUME_STEP);
			default -> ramp;
		};
	}

	/**
	 * Ziel-Dauer der langen Ausfahrt: wächst über die Belastungswochen auf einen Anteil der Event-Dauer.
	 * Lange Events: 40 % → 75 %; mittlere: 60 % → 100 %; kurze: 90 % → 140 % (Grundlage schadet nie);
	 * Ultra: 25 % → 45 % (6 h sind ohnehin die Grenze).
	 */
	static int longRideMinutes(EventProfile profile, Phase phase, int loadWeek, int loadWeeksTotal) {
		int eventMinutes = profile.estimatedMinutes();
		double from;
		double to;
		if (profile.focus() == Focus.ULTRA) {
			from = 0.25;
			to = 0.45;
		} else if (eventMinutes >= 240) {
			from = 0.4;
			to = 0.75;
		} else if (eventMinutes >= 150) {
			from = 0.6;
			to = 1.0;
		} else {
			from = 0.9;
			to = 1.4;
		}
		double progress = loadWeeksTotal <= 1 ? 1 : Math.min(1, (double) loadWeek / (loadWeeksTotal - 1));
		double target = eventMinutes * (from + (to - from) * progress);
		target = Math.clamp(target, MIN_LONG_RIDE, MAX_LONG_RIDE);
		return switch (phase) {
			case RECOVERY -> (int) (target * 0.6);
			case TAPER -> (int) (target * 0.5);
			default -> (int) target;
		};
	}

	/** Lange Events: im Aufbau und in der Spitze jede zweite lange Ausfahrt mit Tempo-Blöcken */
	static boolean useTempoLongRide(EventProfile profile, Phase phase, int loadWeek) {
		boolean buildOrPeak = phase == Phase.BUILD || phase == Phase.PEAK;
		return switch (profile.goal()) {
			// ankommen zählt: lange Ausfahrten bleiben locker
			case FINISH -> false;
			// vorne mitfahren: Renntempo üben – auch bei Ultras, und bei langen Events jede Woche
			case COMPETE -> buildOrPeak && (isLongEvent(profile) || profile.focus() == Focus.ULTRA);
			case GOOD_TIME -> isLongEvent(profile) && buildOrPeak && loadWeek % 2 == 0;
		};
	}

	private static boolean isLongEvent(EventProfile profile) {
		return profile.focus() == Focus.LONG_CLIMBING || profile.focus() == Focus.LONG_FLAT;
	}

	// ---------- harte Workouts ----------

	/** Harte Einheiten pro Woche nach Level und Wochenzeit */
	/** Vorne mitfahren mit genug Zeit (ab 7 h): eine harte Einheit mehr, höchstens 3 */
	static int hardSessionsPerWeek(Level level, double weeklyHours, EventGoal goal) {
		int base = hardSessionsPerWeek(level, weeklyHours);
		return switch (goal) {
			case COMPETE -> weeklyHours >= 7 ? Math.min(3, base + 1) : base;
			case FINISH, GOOD_TIME -> base;
		};
	}

	static int hardSessionsPerWeek(Level level, double weeklyHours) {
		return switch (level) {
			case BEGINNER -> weeklyHours >= MANY_HOURS ? 2 : 1;
			case HOBBY -> weeklyHours >= 4 ? 2 : 1;
			case AMBITIOUS -> weeklyHours >= MANY_HOURS ? 3 : 2;
			case PERFORMANCE -> weeklyHours >= 8 ? 3 : 2;
		};
	}

	static List<WorkoutType> hardWorkoutsFor(Level level, Focus focus, Phase phase, int weekIndex,
			int weeksUntilEvent, double weeklyHours, boolean ftpTest) {
		return hardWorkoutsFor(level, focus, phase, weekIndex, weeksUntilEvent, weeklyHours, ftpTest,
				EventGoal.GOOD_TIME);
	}

	static List<WorkoutType> hardWorkoutsFor(Level level, Focus focus, Phase phase, int weekIndex,
			int weeksUntilEvent, double weeklyHours, boolean ftpTest, EventGoal goal) {
		if (phase == Phase.RECOVERY) {
			return List.of();
		}
		if (phase == Phase.TAPER) {
			// Woche vor dem Event: eine kurze, harte Einheit passend zum Event
			return List.of(focus == Focus.SHORT_INTENSE || focus == Focus.MIXED ? WorkoutType.VO2MAX
					: WorkoutType.THRESHOLD);
		}

		List<WorkoutType> pool = poolFor(focus, phase, level, weeklyHours);
		int count = hardSessionsPerWeek(level, weeklyHours, goal);
		long distinct = pool.stream().distinct().count();
		List<WorkoutType> result = new ArrayList<>();
		// reihum aus dem Pool, möglichst keine Art doppelt in einer Woche
		for (int i = 0; result.size() < count && i < pool.size() * 2; i++) {
			WorkoutType type = pool.get((weekIndex + i) % pool.size());
			if (!result.contains(type) || result.size() >= distinct) {
				result.add(type);
			}
		}
		if (ftpTest) {
			result.set(0, WorkoutType.FTP_TEST);
		}
		return result;
	}

	/** Welche harten Workouts zum Schwerpunkt und zur Phase passen */
	static List<WorkoutType> poolFor(Focus focus, Phase phase, Level level, double weeklyHours) {
		List<WorkoutType> pool = switch (phase) {
			case BASE -> switch (focus) {
				case LONG_FLAT, ULTRA -> List.of(WorkoutType.TEMPO, WorkoutType.SWEET_SPOT);
				default -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.TEMPO);
			};
			case BUILD -> switch (focus) {
				case LONG_CLIMBING -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER);
				case LONG_FLAT -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.TEMPO, WorkoutType.THRESHOLD);
				case ULTRA -> List.of(WorkoutType.TEMPO, WorkoutType.SWEET_SPOT);
				case MIXED -> List.of(WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER, WorkoutType.VO2MAX);
				case SHORT_INTENSE -> List.of(WorkoutType.VO2MAX, WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER);
				case TIME_TRIAL -> List.of(WorkoutType.THRESHOLD, WorkoutType.SWEET_SPOT, WorkoutType.OVER_UNDER);
			};
			default -> switch (focus) { // Spitze
				case LONG_CLIMBING -> List.of(WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER, WorkoutType.VO2MAX);
				case LONG_FLAT -> List.of(WorkoutType.THRESHOLD, WorkoutType.SWEET_SPOT, WorkoutType.VO2MAX);
				case ULTRA -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.TEMPO);
				case MIXED -> List.of(WorkoutType.VO2MAX, WorkoutType.OVER_UNDER, WorkoutType.THRESHOLD);
				case SHORT_INTENSE -> List.of(WorkoutType.VO2MAX, WorkoutType.ANAEROBIC, WorkoutType.SPRINT);
				case TIME_TRIAL -> List.of(WorkoutType.THRESHOLD, WorkoutType.VO2MAX, WorkoutType.OVER_UNDER);
			};
		};
		return pool.stream().map(type -> adjust(type, phase, level, weeklyHours, focus)).toList();
	}

	/**
	 * Wenig Zeit: Tempo wird zu Sweet Spot (mehr Wirkung pro Minute).
	 * Viel Zeit bei Rennen, Kriterien und Zeitfahren: im Aufbau und in der Spitze polarisiert – Sweet Spot wird
	 * zu Schwelle. Für lange Events bleibt Sweet Spot, weil er genau deren Belastung trainiert.
	 * Einsteiger: keine Anaerob-, Sprint- oder Over-Under-Einheiten.
	 */
	private static WorkoutType adjust(WorkoutType type, Phase phase, Level level, double weeklyHours, Focus focus) {
		WorkoutType result = type;
		if (weeklyHours < FEW_HOURS && result == WorkoutType.TEMPO) {
			result = WorkoutType.SWEET_SPOT;
		}
		boolean raceLike = focus == Focus.MIXED || focus == Focus.SHORT_INTENSE || focus == Focus.TIME_TRIAL;
		if (weeklyHours > MANY_HOURS && phase != Phase.BASE && raceLike && result == WorkoutType.SWEET_SPOT) {
			result = WorkoutType.THRESHOLD;
		}
		if (level == Level.BEGINNER && TOO_HARD_FOR_BEGINNERS.contains(result)) {
			result = WorkoutType.THRESHOLD;
		}
		return result;
	}

	// ---------- Wochen zusammensetzen ----------

	private List<PlannedWorkout> planWeek(WeeklyAvailability availability, List<LocalDate> days, WeekPlan plan) {
		if (days.isEmpty()) {
			return List.of();
		}

		// Tag mit der meisten Zeit wird die lange Ausfahrt (bei Gleichstand der spätere, meist Wochenende)
		LocalDate longDay = days.stream()
				.max(Comparator.comparingInt((LocalDate day) -> availableMinutes(availability, day))
						.thenComparing(Comparator.naturalOrder()))
				.orElseThrow();

		// Harte Einheiten nicht am Tag der langen Ausfahrt und nicht direkt davor oder danach
		List<LocalDate> candidates = days.stream()
				.filter(day -> Math.abs(ChronoUnit.DAYS.between(longDay, day)) > 1)
				.sorted(Comparator.comparingInt((LocalDate day) -> availableMinutes(availability, day)).reversed())
				.toList();

		List<PlannedWorkout> week = new ArrayList<>();
		List<LocalDate> hardDays = new ArrayList<>();

		for (WorkoutType type : plan.hardWorkouts()) {
			// passt die geplante Stufe nicht in die Zeit, wird es die nächstleichtere Variante –
			// und passt gar keine, eine kürzere Trainingsart, damit kurze Tage nicht ohne Reiz bleiben
			List<Workout> options = new ArrayList<>();
			for (int step = plan.step(); step >= 0; step--) {
				options.add(library.variant(type, step));
			}
			// passt nicht mal der FTP-Test, gibt es stattdessen eine normale kurze Einheit
			SHORT_FALLBACKS.stream().filter(fallback -> fallback != type)
					.forEach(fallback -> options.add(library.variant(fallback, 0)));
			for (Workout workout : options) {
				// der Test braucht gut eine Stunde: notfalls auch direkt neben der langen Ausfahrt –
				// nicht ideal frisch, aber besser als gar kein Test
				List<LocalDate> possibleDays = workout.getType() == WorkoutType.FTP_TEST
						? java.util.stream.Stream.concat(candidates.stream(),
								days.stream().filter(day -> !day.equals(longDay) && !candidates.contains(day)))
								.toList()
						: candidates;
				var day = possibleDays.stream()
						.filter(candidate -> !hardDays.contains(candidate))
						.filter(candidate -> hardDays.stream()
								.noneMatch(hard -> Math.abs(ChronoUnit.DAYS.between(hard, candidate)) == 1))
						.filter(candidate -> availableMinutes(availability, candidate) >= workout.getDurationInMinutes())
						.findFirst();
				if (day.isPresent()) {
					hardDays.add(day.get());
					week.add(new PlannedWorkout(day.get(), workout, null, plan.phase()));
					break;
				}
			}
		}

		for (LocalDate day : days) {
			if (hardDays.contains(day)) {
				continue;
			}
			int available = availableMinutes(availability, day);
			if (day.equals(longDay)) {
				// Erholung und Tapering: auch kürzer als das, was am Wochenende sonst möglich ist –
				// sonst wäre die lange Ausfahrt bei knapper Zeit genauso lang wie in normalen Wochen
				int possible = (int) (available * switch (plan.phase()) {
					case RECOVERY -> 0.75;
					case TAPER -> 0.6;
					default -> 1.0;
				});
				week.add(longRide(day, Math.min(plan.longRideMinutes(), possible), plan));
			} else {
				// lockere Tage nie länger als die lange Ausfahrt
				int minutes = floorToQuarterHour(Math.min(available * plan.volume(), plan.longRideMinutes()));
				week.add(easyWorkout(day, minutes, available, plan.phase()));
			}
		}

		week.sort(Comparator.comparing(PlannedWorkout::getDate));
		return week;
	}

	private PlannedWorkout longRide(LocalDate day, int targetMinutes, WeekPlan plan) {
		int minutes = floorToQuarterHour(targetMinutes);
		if (minutes < 150) {
			return easyWorkout(day, minutes, targetMinutes, plan.phase());
		}
		if (plan.tempoLongRide() && plan.phase() != Phase.RECOVERY && minutes >= 180) {
			return new PlannedWorkout(day, WorkoutLibrary.longRideWithTempo(minutes), minutes, plan.phase());
		}
		return new PlannedWorkout(day, WorkoutLibrary.longRide(minutes), minutes, plan.phase());
	}

	/** Grundlage ab einer Stunde, sonst lockeres Ausrollen (mindestens 30 min, nie mehr als verfügbar) */
	private PlannedWorkout easyWorkout(LocalDate day, int minutes, int available, Phase phase) {
		if (minutes >= 60) {
			int capped = Math.min(minutes, 300);
			return new PlannedWorkout(day, WorkoutLibrary.endurance(capped), capped, phase);
		}
		int atLeast = Math.min(Math.max(minutes, 30), available);
		return new PlannedWorkout(day, WorkoutLibrary.recovery(atLeast), atLeast, phase);
	}

	/**
	 * Event-Woche: früh in der Woche eine kurze, knackige Einheit, sonst locker und kurz.
	 * Zwei Tage vor dem Event Ruhe, am Vortag ein kurzer Opener.
	 */
	private List<PlannedWorkout> planEventWeek(WeeklyAvailability availability, List<LocalDate> days, LocalDate eventDate,
			EventProfile profile) {
		List<PlannedWorkout> week = new ArrayList<>();
		LocalDate dayBefore = eventDate.minusDays(1);
		LocalDate restDay = eventDate.minusDays(2);
		Workout sharpener = library.variant(
				profile.focus() == Focus.SHORT_INTENSE || profile.focus() == Focus.MIXED ? WorkoutType.VO2MAX
						: WorkoutType.THRESHOLD,
				0);
		Workout opener = library.findById(28L).orElseThrow();

		boolean sharpenerPlaced = false;
		for (LocalDate day : days) {
			int available = availableMinutes(availability, day);
			if (day.equals(restDay)) {
				continue;
			}
			if (day.equals(dayBefore)) {
				if (available >= opener.getDurationInMinutes()) {
					week.add(new PlannedWorkout(day, opener, null, Phase.TAPER));
				}
				continue;
			}
			// die knackige Einheit mit Abstand zum Event (spätestens 3 Tage vorher)
			if (!sharpenerPlaced && day.isBefore(eventDate.minusDays(2))
					&& available >= sharpener.getDurationInMinutes()) {
				week.add(new PlannedWorkout(day, sharpener, null, Phase.TAPER));
				sharpenerPlaced = true;
				continue;
			}
			week.add(easyWorkout(day, Math.min(60, floorToQuarterHour(available * 0.4)), available, Phase.TAPER));
		}
		return week;
	}

	private static int availableMinutes(WeeklyAvailability availability, LocalDate day) {
		return (int) Math.round(availability.hoursOn(day.getDayOfWeek()) * 60);
	}

	/** Abrunden, damit nie mehr Zeit geplant wird, als verfügbar ist */
	private static int floorToQuarterHour(double minutes) {
		return (int) (Math.floor(minutes / 15) * 15);
	}

}

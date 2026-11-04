package io.github.jakobfa.webtechproject.service;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
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
 * - Jede Woche mit Zeit an allen sieben Tagen bekommt einen Pausentag (der Tag mit der knappsten Zeit, bevorzugt
 *   mitten in der Woche).
 * - Der Umfang einer Belastungswoche steigt höchstens um 10 % gegenüber der Belastungswoche davor – auch wenn die
 *   eingetragene Zeit viel mehr hergäbe. Erholung und Tapering sind davon ausgenommen.
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
	/** Wer nur wenig Zeit hat (bis zu dieser Wochenzeit), nutzt sie von Anfang an besser aus */
	static final double SCARCE_HOURS = 8;
	static final double SCARCE_TIME_BONUS = 0.1;

	/** Die lange Ausfahrt und ihr Partner am Back-to-back-Wochenende (Anteil der langen Ausfahrt) */
	static final double BACK_TO_BACK_PARTNER_SHARE = 0.8;
	/** Alle anderen lockeren Tage sind kürzer als die lange Ausfahrt – es gibt höchstens zwei lange Tage */
	static final double OTHER_DAYS_SHARE = 0.6;
	/** Beide Tage eines Back-to-back müssen mindestens so lang sein können, sonst lohnt es sich nicht */
	static final int MIN_BACK_TO_BACK_MINUTES = 180;
	/** Back-to-back erst bei Events ab dieser Dauer (Minuten) */
	static final int BACK_TO_BACK_EVENT_MINUTES = 480;

	/** In Erholungswochen zählt eine Fahrt ab so vielen Minuten als Grundlage (Zone 2) statt als Ausrollen */
	static final int RECOVERY_ENDURANCE_MIN_MINUTES = 45;
	/**
	 * Mit 4 bis unter 7 Wochenstunden bekommt eine Erholungswoche eine kurze, leichte Qualitätseinheit: Bei wenig
	 * Zeit bleibt sonst fast nur Ausrollen übrig. Wer mehr Zeit hat, erholt sich über den Umfang.
	 */
	static final double RECOVERY_TOUCH_MIN_HOURS = 4;
	static final double RECOVERY_TOUCH_MAX_HOURS = 7;
	static final int MIN_WEEKLY_GROWTH_MINUTES = 30;
	/** Beim Kürzen für den Wochendeckel werden lockere Fahrten nicht kürzer als das */
	static final int MIN_CAPPED_RIDE = 45;

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
		return generateSeason(athlete, startDate, weekAvailability, moreEvents, horizon, PlanHistory.NONE);
	}

	/**
	 * Wie oben, aber der Plan richtet sich nach dem, was in den vergangenen Wochen wirklich gefahren wurde:
	 * Der Wochenumfang baut auf den gefahrenen Minuten auf, eine Erholungswoche entfällt nach einer zu schwach
	 * gefahrenen Belastungswoche, und nach längerer Krankheit gibt es einen lockeren Wiedereinstieg.
	 */
	public List<PlannedWorkout> generateSeason(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, List<Event> moreEvents, LocalDate horizon,
			PlanHistory history) {
		return athlete.hasEventGoal()
				? ridesOfSeason(athlete, startDate, weekAvailability, moreEvents, history)
				: generateOpenEnded(athlete, startDate, weekAvailability, horizon, history);
	}

	private List<PlannedWorkout> ridesOfSeason(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, List<Event> moreEvents, PlanHistory history) {
		if (moreEvents.isEmpty()) {
			return generate(athlete, startDate, weekAvailability, history);
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
			List<PlannedWorkout> segment = generate(withEvent(athlete, event), segmentStart, weekAvailability,
					history);
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
		return generate(athlete, startDate, weekAvailability, PlanHistory.NONE);
	}

	List<PlannedWorkout> generate(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, PlanHistory history) {
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
				Math.min(allWeeks, MAX_WEEKS), loadWeeksTotal, history);
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
		return generateOpenEnded(athlete, startDate, weekAvailability, horizon, PlanHistory.NONE);
	}

	List<PlannedWorkout> generateOpenEnded(Athlete athlete, LocalDate startDate,
			Map<LocalDate, WeeklyAvailability> weekAvailability, LocalDate horizon, PlanHistory history) {
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		int weeks = (int) ChronoUnit.WEEKS.between(firstMonday, horizon.with(previousOrSame(DayOfWeek.MONDAY)))
				+ 1;
		if (weeks < 1) {
			return List.of();
		}
		return planWeeks(athlete, EventProfile.of(athlete), openPhases(weeks), startDate, null, weekAvailability,
				weeks, OPEN_LOAD_WEEKS_TO_FULL, history);
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
			int weeks, int loadWeeksTotal, PlanHistory history) {
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		int allWeeks = eventDate == null ? Integer.MAX_VALUE : phases.size();
		List<PlannedWorkout> plan = new ArrayList<>();
		Map<Phase, Integer> weeksInPhase = new EnumMap<>(Phase.class);
		// wie die Wochen ohne Anpassung an die Wirklichkeit geplant waren
		List<Phase> scheduled = new ArrayList<>(phases);
		int loadWeek = 0;
		// Minuten je Tag: Vergangenes aus dem, was gefahren wurde, dazu das, was dieser Plan schon vorsieht. Daraus
		// rechnen die gleitenden Zeitfenster (7 Tage gegen den Schnitt der 28 Tage davor) – nicht die Kalenderwoche.
		Map<LocalDate, Integer> daily = new java.util.HashMap<>(history.dailyMinutes());
		LocalDate dataStart = history.dataStart() != null && history.dataStart().isBefore(startDate)
				? history.dataStart() : startDate;
		// Tage in Erholungswochen zählen nicht für den Schnitt
		java.util.Set<LocalDate> recoveryDays = new java.util.HashSet<>(history.recoveryDays());
		int lastFtpTest = -FTP_TEST_EVERY_WEEKS;
		// ohne Event: passt der Test nicht in die Woche (z. B. Start am Sonntag), kommt er in die nächste
		boolean ftpTestPending = false;
		for (int weekIndex = 0; weekIndex < weeks; weekIndex++) {
			LocalDate monday = firstMonday.plusWeeks(weekIndex);
			WeeklyAvailability availability = weekAvailability.getOrDefault(monday, athlete.getAvailability());
			double weeklyHours = availability.totalHours();
			int weeksUntilEvent = eventDate == null ? Integer.MAX_VALUE : allWeeks - 1 - weekIndex;
			boolean returning = adaptPhase(phases, scheduled, weekIndex, monday, history, weeksUntilEvent, daily,
					recoveryDays, dataStart);
			Phase phase = phases.get(weekIndex);

			List<LocalDate> trainingDays = monday.datesUntil(monday.plusWeeks(1))
					.filter(day -> !day.isBefore(startDate) && (eventDate == null || day.isBefore(eventDate)))
					.filter(day -> availableMinutes(availability, day) > 0)
					.toList();
			// Laufende Woche: nur noch die Tage ab heute planen – was davor war, ist gefahren oder ausgefallen
			boolean currentWeek = history.isCurrentWeek(monday);
			if (currentWeek) {
				trainingDays = trainingDays.stream().filter(day -> !day.isBefore(history.today())).toList();
			}
			// Laufende Woche: Waren die Tage davor alle Trainingstage, muss unter den restlichen ein Ruhetag sein –
			// sonst gäbe es in dieser Woche gar keinen
			int trainedBefore = 0;
			java.util.Set<LocalDate> fixedDays = java.util.Set.of();
			if (currentWeek) {
				for (LocalDate day = monday; day.isBefore(history.today()); day = day.plusDays(1)) {
					if (history.dailyMinutes().getOrDefault(day, 0) > 0) {
						trainedBefore++;
					}
				}
				fixedDays = history.fixedMinutes().entrySet().stream().filter(entry -> entry.getValue() > 0)
						.map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
			}
			trainingDays = withRestDay(trainingDays, availability, trainedBefore, fixedDays);

			if (weeksUntilEvent == 0) {
				plan.addAll(planEventWeek(availability, trainingDays, eventDate, profile));
				continue;
			}

			boolean ftpTest = !returning && (isFtpTestWeek(phases, weekIndex, allWeeks, weeksUntilEvent, lastFtpTest)
					|| (ftpTestPending && phase != Phase.RECOVERY));
			if (ftpTest) {
				lastFtpTest = weekIndex;
			}

			int step = progressionStep(athlete.getLevel(), phase, weeksInPhase.getOrDefault(phase, 0),
					profile.goal());
			weeksInPhase.merge(phase, 1, Integer::sum);

			List<WorkoutType> hardWorkouts = returning ? List.of() // Wiedereinstieg nach Krankheit: nichts Hartes
					: hardWorkoutsFor(athlete.getLevel(), profile.focus(), phase, weekIndex, weeksUntilEvent,
							weeklyHours, ftpTest, profile.goal());
			// Vorwoche deutlich härter als geplant: kein Zuwachs gegenüber dem Schnitt, eine harte Einheit weniger
			boolean overreached = false;
			if (history.applies(monday) && isLoadPhase(phase)) {
				var previous = history.before(monday);
				overreached = previous != null && previous.overreached() && !ftpTest;
			}
			if (currentWeek) {
				// Harte Einheiten, die diese Woche schon gefahren wurden, zählen mit – sonst gäbe es dieselbe harte
				// Einheit gleich noch einmal (am Tag danach)
				hardWorkouts = withoutDoneHard(hardWorkouts, history.doneHard().types());
				var done = history.currentWeek();
				// Schon so viel gefahren wie die ganze Woche vorsieht: der Rest der Woche bleibt locker
				if (done.plannedTss() >= PlanHistory.MIN_PLANNED_TSS_FOR_RATIO && done.actualTss() >= done.plannedTss()
						&& !ftpTest) {
					hardWorkouts = List.of();
				}
			}
			// Gleitendes Zeitfenster: höchstens so viel in 7 Tagen, wie der Schnitt der letzten 28 Tage hergibt
			RollingCap rolling = isPastVirtualWeek(history, monday) ? null
					: rollingCap(daily, recoveryDays, dataStart, monday, phase, overreached, memoryAllowed(history, monday));
			if (rolling != null) {
				rolling = new RollingCap(rolling.daily(), rolling.weeklyCapMinutes(), history.fixedMinutes());
			}

			WeekPlan week = new WeekPlan(phase, weekIndex, step,
					volumeFactor(phase, loadWeek, startVolume(athlete.getLevel(), weeklyHours)),
					longRideMinutes(profile, phase, loadWeek, loadWeeksTotal),
					useTempoLongRide(profile, phase, loadWeek),
					hardWorkouts, rolling,
					backToBack(athlete.getLevel(), profile, phase, loadWeek),
					currentWeek ? history.doneHard().days() : java.util.Set.of(), profile.focus(),
					history.doneHard().upcomingFtpTestDays(), athlete.getLevel() != Level.BEGINNER);
			List<PlannedWorkout> planned = planWeek(availability, trainingDays, week);
			if (overreached) {
				// nicht alles Gewünschte passt in die Tage: eine weniger als tatsächlich eingeplant wurde
				long placed = planned.stream().filter(this::isHardWorkout).count();
				if (placed > 1) {
					week = new WeekPlan(week.phase(), week.weekIndex(), week.step(), week.volume(),
							week.longRideMinutes(), week.tempoLongRide(),
							hardWorkouts.subList(0, (int) placed - 1), week.rolling(), week.backToBack(),
							week.doneHardDays(), week.focus(), week.ftpTestDays(), week.specialties());
					planned = planWeek(availability, trainingDays, week);
				}
			}
			plan.addAll(planned);
			if (phase == Phase.RECOVERY) {
				monday.datesUntil(monday.plusWeeks(1)).forEach(recoveryDays::add);
			}
			// Vergangene Tage bleiben, wie sie wirklich waren; alles ab heute (oder ohne Historie alles) zählt geplant
			for (PlannedWorkout workout : planned) {
				if (history.isEmpty() || !workout.getDate().isBefore(history.today() == null ? history.thisMonday()
						: history.today())) {
					// feststehende Einheiten (selbst getauscht/angepasst) zählen mit ihren echten Minuten
					if (!history.fixedMinutes().containsKey(workout.getDate())) {
						daily.merge(workout.getDate(), library.resolve(workout.getWorkoutId(), workout.getDurationMinutes())
								.orElseThrow().getDurationInMinutes(), Integer::sum);
					}
				}
			}
			boolean testMissed = ftpTest
					&& planned.stream().noneMatch(workout -> workout.getWorkoutId() == WorkoutLibrary.FTP_TEST);
			// offen bleibt er, bis er eingeplant werden konnte (in Erholungswochen wird nicht getestet)
			ftpTestPending = eventDate == null && (testMissed || (ftpTestPending && !ftpTest));

			if (isLoadPhase(phase)) {
				loadWeek++;
			}
		}
		return plan;
	}

	/**
	 * Passt die Phase einer laufenden oder kommenden Woche an die Wirklichkeit an. Liefert true, wenn die Woche
	 * ein lockerer Wiedereinstieg nach Krankheit ist.
	 * <ul>
	 * <li>Wiedereinstieg: die Woche wird zur Erholungswoche, ohne harte Einheiten.</li>
	 * <li>Auf eine Belastungswoche, die zu weniger als 70 % gefahren wurde, folgt keine Erholungswoche: Man hat sich
	 * nicht überlastet, sondern zu wenig getan (oder war krank) – die Woche davor wird wiederholt.</li>
	 * <li>Ebenso, wenn die letzten 3 Wochen deutlich weniger waren als der Schnitt der 4 Wochen davor (siehe
	 * {@link #builtUp}): z. B. nach einem Rennen oder Urlaub, auf den ruhige Wochen folgten. Dann hat sich keine
	 * Ermüdung angesammelt, die eine Erholungswoche abbauen müsste.</li>
	 * </ul>
	 * Das Tapering und die Event-Woche bleiben, wie sie sind.
	 */
	private static boolean adaptPhase(List<Phase> phases, List<Phase> scheduled, int weekIndex, LocalDate monday,
			PlanHistory history, int weeksUntilEvent, Map<LocalDate, Integer> daily,
			java.util.Set<LocalDate> recoveryDays, LocalDate dataStart) {
		if (!history.applies(monday) || phases.get(weekIndex) == Phase.TAPER || weeksUntilEvent <= 1) {
			return false;
		}
		if (history.isReentryWeek(monday)) {
			phases.set(weekIndex, Phase.RECOVERY);
			return true;
		}
		if (weekIndex > 0 && phases.get(weekIndex) == Phase.RECOVERY && isLoadPhase(scheduled.get(weekIndex - 1))) {
			var previous = history.before(monday);
			Double recent = history.averageFulfilment(monday, PlanHistory.RECENT_WEEKS);
			boolean weak = (previous != null && previous.fulfilment() < PlanHistory.MIN_FULFILMENT)
					|| (recent != null && recent < PlanHistory.MIN_RECENT_FULFILMENT);
			// nur, wo der Block schon größtenteils gefahren ist (laufende und nächste Woche); weiter vorn zählt der Plan
			boolean nearNow = !monday.isAfter(history.thisMonday().plusWeeks(1));
			boolean rested = nearNow && loadWeeksBefore(phases, weekIndex) < MAX_LOAD_WEEKS_WITHOUT_RECOVERY
					&& !builtUp(daily, recoveryDays, dataStart, monday);
			// direkt nach einem Wiedereinstieg nicht gleich noch eine Erholungswoche
			if (weak || rested || history.isReentryWeek(monday.minusWeeks(1))) {
				phases.set(weekIndex, scheduled.get(weekIndex - 1));
			}
		}
		return false;
	}

	/** So viele Tage vor einer Erholungswoche zählen als der Block, dessen Ermüdung sie abbauen soll */
	static final int LOAD_BLOCK_DAYS = 21;
	/** Unter so viel vom Schnitt davor gelten die letzten 3 Wochen als ruhig (kein Aufbau von Ermüdung) */
	static final double RESTED_BLOCK_RATIO = 0.9;
	/** Spätestens nach so vielen Belastungswochen am Stück kommt die Erholungswoche, auch ohne Anstieg */
	static final int MAX_LOAD_WEEKS_WITHOUT_RECOVERY = 6;

	/**
	 * Hat sich vor monday Belastung aufgebaut? Nein nur, wenn die letzten 3 Wochen im Schnitt unter 90 % der 4 Wochen
	 * davor lagen (je Tag höchstens BASELINE_MAX_DAY_MINUTES, wie beim Wochendeckel). Ohne genug Daten für den Vergleich:
	 * ja – dann bleibt die Erholungswoche, wie sie geplant ist.
	 */
	static boolean builtUp(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays, LocalDate dataStart,
			LocalDate monday) {
		Double before = windowAverage(daily, recoveryDays, dataStart, monday.minusDays(LOAD_BLOCK_DAYS),
				MEMORY_MIN_DAYS);
		if (before == null || before <= 0) {
			return true;
		}
		int sum = 0;
		int counted = 0;
		for (int back = 1; back <= LOAD_BLOCK_DAYS; back++) {
			LocalDate day = monday.minusDays(back);
			if (day.isBefore(dataStart)) {
				continue;
			}
			sum += Math.min(daily.getOrDefault(day, 0), BASELINE_MAX_DAY_MINUTES);
			counted++;
		}
		return counted < LOAD_BLOCK_DAYS || sum * 7.0 / counted >= before * RESTED_BLOCK_RATIO;
	}

	/** Belastungswochen direkt vor weekIndex (bis zur letzten Erholungswoche) */
	private static int loadWeeksBefore(List<Phase> phases, int weekIndex) {
		int count = 0;
		for (int i = weekIndex - 1; i >= 0 && isLoadPhase(phases.get(i)); i--) {
			count++;
		}
		return count;
	}

	/** Was in einer normalen Woche geplant wird */
	record WeekPlan(Phase phase, int weekIndex, int step, double volume, int longRideMinutes,
			boolean tempoLongRide, List<WorkoutType> hardWorkouts, RollingCap rolling, boolean backToBack,
			java.util.Set<LocalDate> doneHardDays, EventProfile.Focus focus, java.util.Set<LocalDate> ftpTestDays,
			boolean specialties) {
	}

	/**
	 * Obergrenze für jedes 7-Tage-Fenster: daily = Minuten je Tag bisher (vergangen und schon geplant),
	 * weeklyCapMinutes = so viel darf in 7 aufeinanderfolgenden Tagen höchstens zusammenkommen.
	 */
	record RollingCap(Map<LocalDate, Integer> daily, int weeklyCapMinutes, Map<LocalDate, Integer> fixed) {
	}

	/** Akut (7 Tage) zu chronisch (Schnitt der 28 Tage davor): ca. +7 bis 10 % pro Woche, wenn es stetig steigt */
	static final double ACUTE_CHRONIC_RATIO = 1.18;
	/** Erholungswochen: so viel vom Schnitt der letzten 28 Tage */
	static final double RECOVERY_ACUTE_RATIO = 0.75;
	/** Zu hart gefahren: kein Zuwachs über den Schnitt hinaus */
	static final double OVERREACHED_ACUTE_RATIO = 1.0;
	/** Auch mit wenig Daten darf die nächste Woche mindestens so viel über dem Schnitt liegen */
	static final double EARLY_MIN_RATIO = 1.12;
	static final int BASELINE_DAYS = 28;
	/** Mit weniger Tagen Daten gibt es noch keinen verlässlichen Schnitt – dann kein Deckel */
	static final int MIN_BASELINE_DAYS = 3;
	/**
	 * Ein einzelner Tag zählt im Schnitt höchstens so viel: Ein Rennen oder eine Ultra-Fahrt (z. B. 12 Stunden) soll die
	 * Obergrenze der Folgewochen nicht anheben – sie wären sonst ein Sprung weit über das, was sonst gefahren wird.
	 */
	static final int BASELINE_MAX_DAY_MINUTES = 360;

	/**
	 * Wie weit liegt die letzte Woche über dem Schnitt der Wochen davor, wenn der Umfang stetig um 10 % pro Woche
	 * wächst? Mit nur einer Woche Daten +10 %, mit zwei ca. +15 %, ab drei ca. +20 %. So darf der Plan am Anfang
	 * nicht schneller wachsen als später.
	 */
	static double steadyGrowthRatio(double weeksOfData) {
		double n = Math.max(weeksOfData, 0.05);
		return n * 0.1 / (1 - Math.pow(1.1, -n));
	}

	/** Gedächtnis nur ohne Krankheit oder Verletzung in den Wochen, auf die es zurückgreift */
	static boolean memoryAllowed(PlanHistory history, LocalDate monday) {
		if (history.reentryMonday() != null) {
			return false;
		}
		LocalDate from = monday.minusWeeks(MEMORY_WEEKS + 4L);
		return history.weeks().entrySet().stream()
				.noneMatch(entry -> !entry.getKey().isBefore(from) && entry.getValue().sickDays() > 0);
	}

	private static boolean isPastVirtualWeek(PlanHistory history, LocalDate monday) {
		return !history.isEmpty() && monday.isBefore(history.thisMonday());
	}

	/**
	 * Trainingsgedächtnis: Fitness baut sich über Wochen ab, nicht über Tage. Nach einer Off-Season oder ein paar
	 * ruhigen Wochen zählt deshalb auch das beste 28-Tage-Fenster der letzten Wochen – je Woche Abstand um 5 %
	 * abgeschwächt (grob wie die Fitness abklingt). Nur Fenster mit genug Daten zählen, sonst würde eine kurze
	 * Spitze hochgerechnet. Nach Krankheit oder Verletzung gilt es nicht (dafür gibt es den Wiedereinstieg).
	 */
	static final int MEMORY_WEEKS = 8;
	static final double MEMORY_WEEKLY_DECAY = 0.95;
	static final int MEMORY_MIN_DAYS = 21;

	/** Schnitt pro Woche aus den bis zu 28 Tagen vor end (ohne Erholungstage, ohne Tage vor dataStart) */
	private static Double windowAverage(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate end, int minDays) {
		int sum = 0;
		int countedDays = 0;
		for (int back = 1; back <= BASELINE_DAYS; back++) {
			LocalDate day = end.minusDays(back);
			if (day.isBefore(dataStart) || recoveryDays.contains(day)) {
				continue; // Erholungstage verfälschen den Schnitt der Belastung
			}
			sum += Math.min(daily.getOrDefault(day, 0), BASELINE_MAX_DAY_MINUTES);
			countedDays++;
		}
		return countedDays < minDays ? null : sum * 7.0 / countedDays;
	}

	/** Das Gedächtnis (siehe MEMORY_WEEKS): bestes abgeschwächtes Fenster vor monday, 0 ohne genug Daten */
	static double memoryWeeklyMinutes(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate monday) {
		double best = 0;
		for (int k = 1; k <= MEMORY_WEEKS; k++) {
			Double average = windowAverage(daily, recoveryDays, dataStart, monday.minusWeeks(k), MEMORY_MIN_DAYS);
			if (average != null) {
				best = Math.max(best, average * Math.pow(MEMORY_WEEKLY_DECAY, k));
			}
		}
		return best;
	}

	/** Gezählte Tage im 28-Tage-Fenster vor monday */
	private static int countedDays(java.util.Set<LocalDate> recoveryDays, LocalDate dataStart, LocalDate monday) {
		int counted = 0;
		for (int back = 1; back <= BASELINE_DAYS; back++) {
			LocalDate day = monday.minusDays(back);
			if (!day.isBefore(dataStart) && !recoveryDays.contains(day)) {
				counted++;
			}
		}
		return counted;
	}

	/**
	 * Schnitt der Belastung pro Woche aus den bis zu 28 Tagen vor monday (ohne Erholungstage und mit hochgerechneter
	 * Kurzzeit am Anfang), mindestens das Trainingsgedächtnis; null, wenn es noch zu wenig Daten gibt.
	 */
	static Double baselineWeeklyMinutes(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate monday) {
		return baselineWeeklyMinutes(daily, recoveryDays, dataStart, monday, true);
	}

	static Double baselineWeeklyMinutes(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate monday, boolean withMemory) {
		Double recent = windowAverage(daily, recoveryDays, dataStart, monday, MIN_BASELINE_DAYS);
		if (recent == null) {
			return null;
		}
		return withMemory ? Math.max(recent, memoryWeeklyMinutes(daily, recoveryDays, dataStart, monday)) : recent;
	}

	/** Der Deckel für die 7-Tage-Fenster dieser Woche, null = keiner (zu wenig Daten, Tapering) */
	static RollingCap rollingCap(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate monday, Phase phase, boolean overreached) {
		return rollingCap(daily, recoveryDays, dataStart, monday, phase, overreached, true);
	}

	/** Wie oben; withMemory = false nach Krankheit oder Verletzung (dann nur die letzten 28 Tage) */
	static RollingCap rollingCap(Map<LocalDate, Integer> daily, java.util.Set<LocalDate> recoveryDays,
			LocalDate dataStart, LocalDate monday, Phase phase, boolean overreached, boolean withMemory) {
		if (!isLoadPhase(phase) && phase != Phase.RECOVERY) {
			return null;
		}
		Double weeklyAverage = baselineWeeklyMinutes(daily, recoveryDays, dataStart, monday, withMemory);
		if (weeklyAverage == null || weeklyAverage <= 0) {
			return null;
		}
		int countedDays = countedDays(recoveryDays, dataStart, monday);
		double ratio = phase == Phase.RECOVERY ? RECOVERY_ACUTE_RATIO
				: overreached ? OVERREACHED_ACUTE_RATIO : Math.min(ACUTE_CHRONIC_RATIO, Math.max(steadyGrowthRatio(countedDays / 7.0), EARLY_MIN_RATIO));
		// Belastungswochen dürfen mindestens eine halbe Stunde über den Schnitt (sonst bremst der Prozentsatz bei kleinen
		// Umfängen); Erholungswochen bleiben unter dem Schnitt
		double cap = phase == Phase.RECOVERY ? weeklyAverage * ratio
				: Math.max(weeklyAverage * ratio, weeklyAverage + MIN_WEEKLY_GROWTH_MINUTES);
		return new RollingCap(daily, (int) cap, Map.of());
	}

	/** Die harten Einheiten der Woche ohne die, die schon gefahren wurden (je gefahrene Einheit fällt eine weg) */
	static List<WorkoutType> withoutDoneHard(List<WorkoutType> hardWorkouts, List<WorkoutType> doneTypes) {
		List<WorkoutType> remaining = new ArrayList<>(hardWorkouts);
		for (WorkoutType done : doneTypes) {
			if (!remaining.remove(done) && !remaining.isEmpty()) {
				remaining.remove(remaining.size() - 1);
			}
		}
		return remaining;
	}

	private boolean isHardWorkout(PlannedWorkout planned) {
		return !Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE)
				.contains(library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType());
	}

	private int minutesOf(List<PlannedWorkout> week) {
		return week.stream()
				.mapToInt(planned -> library.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
						.orElseThrow().getDurationInMinutes())
				.sum();
	}

	/**
	 * Hat eine Woche an allen sieben Tagen Zeit, bleibt ein Tag frei: der mit der knappsten Zeit, bei Gleichstand
	 * der, der der Wochenmitte am nächsten liegt (so grenzt er nicht an die lange Ausfahrt am Wochenende).
	 */
	static List<LocalDate> withRestDay(List<LocalDate> days, WeeklyAvailability availability) {
		return withRestDay(days, availability, 0, java.util.Set.of());
	}

	/**
	 * Wie oben; trainedBefore = so viele Tage der Woche (vor den hier geplanten) waren schon Trainingstage, avoid =
	 * Tage mit feststehender Einheit (selbst gewählt, abgehakt): Sie werden nicht zum Ruhetag.
	 */
	static List<LocalDate> withRestDay(List<LocalDate> days, WeeklyAvailability availability, int trainedBefore,
			java.util.Set<LocalDate> avoid) {
		if (days.size() + trainedBefore < 7) {
			return days;
		}
		List<LocalDate> candidates = days.stream().filter(day -> !avoid.contains(day)).toList();
		if (candidates.isEmpty()) {
			return days;
		}
		LocalDate rest = candidates.stream()
				.min(Comparator.comparingInt((LocalDate day) -> availableMinutes(availability, day))
						.thenComparingInt(day -> Math.abs(day.getDayOfWeek().getValue() - DayOfWeek.WEDNESDAY.getValue())))
				.orElseThrow();
		return days.stream().filter(day -> !day.equals(rest)).toList();
	}

	// ---------- Phasen ----------

	/**
	 * Phasen rückwärts vom Event: zuletzt Tapering (Event-Woche + eine Woche), davor Spitze, davor Aufbau
	 * (ca. 40 % der übrigen Wochen), ganz vorn Grundlage. Nach höchstens 3 Belastungswochen folgt eine
	 * Erholungswoche, auch in der Spitze – nur die letzte Woche vor dem Tapering bleibt Belastung (das Tapering
	 * ist selbst die Entlastung). So gibt es nie mehr als 4 harte Wochen am Stück.
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
		int loadSpan = rest + peak;
		for (int i = 0; i < loadSpan; i++) {
			boolean lastBeforeTaper = i == loadSpan - 1;
			if (loaded == 3 && !lastBeforeTaper) {
				phases.set(i, Phase.RECOVERY);
				loaded = 0;
			} else {
				loaded++;
			}
		}
		return phases;
	}

	static boolean isLoadPhase(Phase phase) {
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
	 * Steigerungsstufe der harten Workouts: in Grundlage und Aufbau alle zwei Wochen eine Stufe schwerer
	 * (der Aufbau startet eine Stufe höher, damit es nach der Grundlage nicht wieder leichter wird). Die Spitze
	 * ist der härteste Block: sie beginnt eine Stufe unter ihrem Maximum und darf eine Stufe über das hinaus,
	 * was dem Level sonst zugemutet wird. Im Tapering bleibt es moderat.
	 * Einsteiger und Hobbyfahrer bleiben auf den leichteren Stufen.
	 */
	static int progressionStep(Level level, Phase phase, int weekInPhase) {
		return progressionStep(level, phase, weekInPhase, EventGoal.GOOD_TIME);
	}

	/** Vorne mitfahren: eine Stufe schwerer (auch über die Level-Grenze), ankommen: eine leichter */
	static int progressionStep(Level level, Phase phase, int weekInPhase, EventGoal goal) {
		int maxStep = switch (level) {
			case BEGINNER -> 1;
			case HOBBY -> 2;
			case AMBITIOUS -> 4;
			case PERFORMANCE -> HARDEST_STEP;
		};
		int top = phase == Phase.PEAK ? Math.min(HARDEST_STEP, maxStep + 1) : maxStep;
		int step = switch (phase) {
			case PEAK -> top - 1 + weekInPhase;
			case BUILD -> 1 + weekInPhase / 2;
			case TAPER -> 1;
			default -> weekInPhase / 2;
		};
		return switch (goal) {
			case COMPETE -> Math.min(step + 1, Math.min(top + 1, HARDEST_STEP));
			case FINISH -> Math.max(0, Math.min(step, top) - 1);
			case GOOD_TIME -> Math.min(step, top);
		};
	}

	/**
	 * Höchste Stufe in der Bibliothek (z. B. Tempo 2 × 45, Schwelle 2 × 30). Die Stufen über 3 machen die
	 * Blöcke länger statt härter, damit die Steigerung auch über lange Pläne trägt; Arten mit weniger Stufen
	 * (VO2max, Over-Unders) bleiben auf ihrer schwersten.
	 */
	static final int HARDEST_STEP = 5;

	// ---------- Umfang ----------

	/** Anteil der verfügbaren Zeit, der für lockere Fahrten genutzt wird */
	static double volumeFactor(Phase phase, int loadWeek) {
		return volumeFactor(phase, loadWeek, START_VOLUME);
	}

	/**
	 * Mit welchem Anteil der verfügbaren Zeit die erste Belastungswoche startet: je höher das Level, desto mehr.
	 * Die Zeit ist immer die Obergrenze; wer wenig davon hat, nutzt sie von Anfang an besser aus – dort ist die
	 * Zeit der Engpass, nicht die Belastbarkeit.
	 */
	static double startVolume(Level level, double weeklyHours) {
		double byLevel = switch (level) {
			case BEGINNER -> 0.5;
			case HOBBY -> 0.6;
			case AMBITIOUS -> 0.7;
			case PERFORMANCE -> 0.8;
		};
		return Math.min(1.0, weeklyHours <= SCARCE_HOURS ? byLevel + SCARCE_TIME_BONUS : byLevel);
	}

	static double volumeFactor(Phase phase, int loadWeek, double startVolume) {
		double ramp = Math.min(1.0, startVolume + VOLUME_STEP * loadWeek);
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

	/** Lange Events und Ultras: im Aufbau und in der Spitze jede zweite lange Ausfahrt mit Tempo-Blöcken */
	static boolean useTempoLongRide(EventProfile profile, Phase phase, int loadWeek) {
		boolean buildOrPeak = phase == Phase.BUILD || phase == Phase.PEAK;
		return switch (profile.goal()) {
			// ankommen zählt: lange Ausfahrten bleiben locker
			case FINISH -> false;
			// vorne mitfahren: Renntempo üben – auch bei Ultras, und bei langen Events jede Woche
			case COMPETE -> buildOrPeak && (isLongEvent(profile) || profile.focus() == Focus.ULTRA);
			// Ultras: über Stunden 65–80 % FTP halten ist genau der Reiz, den die lange Ausfahrt hier setzen soll
			case GOOD_TIME -> (isLongEvent(profile) || profile.focus() == Focus.ULTRA) && buildOrPeak
					&& loadWeek % 2 == 0;
		};
	}

	/**
	 * Zwei lange Tage hintereinander (Ermüdung tragen, Verpflegung üben) trainieren Mehrtages-Events, sind aber
	 * ein starker Reiz: nur ab Ambitioniert, bei langen Events, im Aufbau (nicht in Erholung und Tapering) und
	 * nur in jeder zweiten Belastungswoche.
	 */
	static boolean backToBack(Level level, EventProfile profile, Phase phase, int loadWeek) {
		boolean strongEnough = level == Level.AMBITIOUS || level == Level.PERFORMANCE;
		boolean longEvent = profile.focus() == Focus.ULTRA || profile.estimatedMinutes() >= BACK_TO_BACK_EVENT_MINUTES;
		boolean loadPhase = phase == Phase.BASE || phase == Phase.BUILD || phase == Phase.PEAK;
		return strongEnough && longEvent && loadPhase && loadWeek % 2 == 1;
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
			// Erholung heißt weniger Umfang, nicht null Reiz: ab Hobby mit etwas Zeit eine kurze, leichte Einheit
			// (kleinste Stufe) hält die Beine wach – sonst fällt die Belastung um über 70 % statt um 30–50 %
			return level != Level.BEGINNER && weeklyHours >= RECOVERY_TOUCH_MIN_HOURS
					&& weeklyHours < RECOVERY_TOUCH_MAX_HOURS
					? List.of(WorkoutType.SWEET_SPOT) : List.of();
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
				// auch für Ultras: ab und zu VO2max und Schwelle heben die Decke – dieselbe Wattzahl wird leichter
				case ULTRA -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.THRESHOLD, WorkoutType.TEMPO,
						WorkoutType.VO2MAX);
				case MIXED -> List.of(WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER, WorkoutType.VO2MAX);
				// Kriterium braucht auch im Aufbau Antritte: neben Schwelle und Over-Unders eine anaerobe Einheit im Wechsel
				case SHORT_INTENSE -> List.of(WorkoutType.VO2MAX, WorkoutType.THRESHOLD, WorkoutType.ANAEROBIC,
						WorkoutType.OVER_UNDER);
				case TIME_TRIAL -> List.of(WorkoutType.THRESHOLD, WorkoutType.SWEET_SPOT, WorkoutType.OVER_UNDER);
			};
			default -> switch (focus) { // Spitze
				case LONG_CLIMBING -> List.of(WorkoutType.THRESHOLD, WorkoutType.OVER_UNDER, WorkoutType.VO2MAX);
				case LONG_FLAT -> List.of(WorkoutType.THRESHOLD, WorkoutType.SWEET_SPOT, WorkoutType.VO2MAX);
				// Ultra: spezifisch – lange gleichmäßige Blöcke, dazu Schwelle, damit die Intensität nicht verloren geht
				case ULTRA -> List.of(WorkoutType.SWEET_SPOT, WorkoutType.THRESHOLD, WorkoutType.TEMPO);
				case MIXED -> List.of(WorkoutType.VO2MAX, WorkoutType.OVER_UNDER, WorkoutType.THRESHOLD);
				// Kriterium: Antritte und Sprints, aber genug VO2max und Over-Unders für die harten Runden dazwischen
				case SHORT_INTENSE -> List.of(WorkoutType.VO2MAX, WorkoutType.ANAEROBIC, WorkoutType.OVER_UNDER,
						WorkoutType.SPRINT);
				case TIME_TRIAL -> List.of(WorkoutType.THRESHOLD, WorkoutType.VO2MAX, WorkoutType.OVER_UNDER);
			};
		};
		if (phase == Phase.BASE && level != Level.BEGINNER) {
			// auch in der Grundlage ab und zu ein kurzer, harter Reiz (polarisiert) – etwa jede zweite Woche
			pool = List.of(pool.get(0), pool.get(1), pool.get(0), WorkoutType.VO2MAX);
		}
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

		// Back-to-back: der Tag davor wird die lange Ausfahrt, der mit der meisten Zeit ihr kürzerer Partner
		LocalDate partnerDay = null;
		if (plan.backToBack()) {
			LocalDate before = longDay.minusDays(1);
			if (days.contains(before) && availableMinutes(availability, before) >= MIN_BACK_TO_BACK_MINUTES
					&& availableMinutes(availability, longDay) >= MIN_BACK_TO_BACK_MINUTES) {
				partnerDay = longDay;
				longDay = before;
			}
		}
		LocalDate firstLong = longDay;
		LocalDate lastLong = partnerDay != null ? partnerDay : longDay;
		LocalDate partner = partnerDay;

		// Harte Einheiten nicht am Tag der langen Ausfahrt (bzw. des Back-to-back) und nicht direkt davor oder
		// danach. Sie sind kurz und kommen deshalb auf den Tag mit der knappsten Zeit, auf den sie noch passen –
		// die Tage mit viel Zeit bleiben für Umfang (Grundlage, lange Ausfahrt). Bei gleicher Zeit gilt der frühere.
		// Der Tag nach einem großen Umfangstag (meist der Montag nach dem Wochenende) kommt nur zum Zug, wenn sonst
		// nichts passt – mit müden Beinen leidet die Qualität der Intervalle.
		List<LocalDate> candidates = days.stream()
				.filter(day -> day.isBefore(firstLong.minusDays(1)) || day.isAfter(lastLong.plusDays(1)))
				.sorted(Comparator.comparing((LocalDate day) -> isAfterBigDay(availability, day))
						.thenComparingInt(day -> availableMinutes(availability, day)))
				.toList();

		List<PlannedWorkout> week = new ArrayList<>();
		// schon gefahrene harte Tage dieser Woche: sie belegen ihren Tag, der Tag danach bleibt locker
		List<LocalDate> hardDays = new ArrayList<>(plan.doneHardDays());
		// Tage, die frei bleiben: vor dem FTP-Test braucht es einen Pausentag, sonst misst er Müdigkeit statt FTP
		java.util.Set<LocalDate> restDays = new java.util.HashSet<>();
		// Ein schon feststehender FTP-Test (selbst gewählt): der Tag davor bleibt frei – es sei denn, es ist die lange
		// Ausfahrt (die bleibt)
		for (LocalDate testDay : plan.ftpTestDays()) {
			if (!testDay.minusDays(1).equals(firstLong)) {
				restDays.add(testDay.minusDays(1));
			}
		}
		// Erholungswoche mit vielen Trainingstagen: der Tag nach dem großen Umfangstag (meist der Montag nach dem
		// Belastungs-Wochenende) bleibt ganz frei – statt jeden Tag dieselbe kurze Grundlage
		if (plan.phase() == Phase.RECOVERY && days.size() >= RECOVERY_REST_MIN_DAYS) {
			days.stream().filter(day -> !day.equals(firstLong) && !day.equals(partner))
					.filter(day -> isAfterBigDay(availability, day)).findFirst().ifPresent(restDays::add);
		}

		// Abwechslung: je Trainingsart jede Woche das nächste Workout derselben Stufe (zweimal dieselbe Art in
		// einer Woche: zwei verschiedene)
		int weekOfYear = days.getFirst().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		java.util.Map<WorkoutType, Integer> sameTypeThisWeek = new java.util.EnumMap<>(WorkoutType.class);
		for (WorkoutType type : plan.hardWorkouts()) {
			int rotation = weekOfYear + sameTypeThisWeek.merge(type, 1, Integer::sum) - 1;
			// passt die geplante Stufe nicht in die Zeit, wird es die nächstleichtere Variante –
			// und passt gar keine, eine kürzere Trainingsart, damit kurze Tage nicht ohne Reiz bleiben
			List<Workout> options = new ArrayList<>();
			// Spezialeinheiten für dieses Ziel (z. B. Kraftausdauer für Ultra) kommen reihum zuerst an die Reihe
			if (plan.specialties() && Math.floorMod(rotation + type.ordinal(), SPECIALTY_EVERY_NTH_WEEK) == 0) {
				options.addAll(rotated(library.specialtiesFor(type, plan.step(), plan.focus()), rotation));
			}
			// in Erholungswochen immer die kleinste Stufe
			for (int step = plan.phase() == Phase.RECOVERY ? 0 : plan.step(); step >= 0; step--) {
				options.addAll(rotated(library.alternatives(type, step, plan.focus()), rotation));
			}
			// passt nicht mal der FTP-Test, gibt es stattdessen eine normale kurze Einheit
			SHORT_FALLBACKS.stream().filter(fallback -> fallback != type)
					.forEach(fallback -> options.add(library.variant(fallback, 0)));
			for (Workout workout : options) {
				// der Test braucht gut eine Stunde: notfalls auch direkt neben der langen Ausfahrt –
				// nicht ideal frisch, aber besser als gar kein Test
				boolean ftpTest = workout.getType() == WorkoutType.FTP_TEST;
				List<LocalDate> possibleDays = ftpTest
						? java.util.stream.Stream.concat(candidates.stream(),
								days.stream().filter(day -> !day.equals(firstLong) && !day.equals(partner)
										&& !candidates.contains(day)))
								.filter(candidate -> hasRestDayBefore(candidate, days, availability, firstLong, partner))
								// zuerst Tage, an denen der Vortag ohnehin frei ist
								.sorted(Comparator.comparing((LocalDate candidate) -> days.contains(candidate.minusDays(1))))
								.toList()
						: candidates;
				var day = possibleDays.stream()
						.filter(candidate -> !hardDays.contains(candidate) && !restDays.contains(candidate))
						.filter(candidate -> hardDays.stream()
								.noneMatch(hard -> Math.abs(ChronoUnit.DAYS.between(hard, candidate)) == 1))
						.filter(candidate -> fitsInto(workout, availableMinutes(availability, candidate)))
						.findFirst();
				if (day.isPresent()) {
					hardDays.add(day.get());
					if (ftpTest) {
						restDays.add(day.get().minusDays(1)); // der Tag davor bleibt frei (falls er ein Trainingstag wäre)
					}
					int available = availableMinutes(availability, day.get());
					// etwas zu lang: Aufwärmen und Ausfahren kürzen statt eine leichtere Stufe zu nehmen
					Integer length = workout.getDurationInMinutes() > available ? available : null;
					week.add(new PlannedWorkout(day.get(), workout, length, plan.phase()));
					break;
				}
			}
		}

		// Minuten der lockeren Tage und der langen Ausfahrt erst festlegen, dann für den Wochendeckel kürzen
		java.util.Map<LocalDate, Integer> flexible = new java.util.TreeMap<>();
		for (LocalDate day : days) {
			if (hardDays.contains(day) || restDays.contains(day)) {
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
				flexible.put(day, Math.min(plan.longRideMinutes(), possible));
			} else if (day.equals(partner)) {
				// zweiter langer Tag: kürzer als der erste, damit die Ermüdung tragbar bleibt
				flexible.put(day, floorToQuarterHour(Math.min(available * plan.volume(),
						flexible.get(firstLong) * BACK_TO_BACK_PARTNER_SHARE)));
			} else {
				// alle anderen lockeren Tage deutlich kürzer als die lange Ausfahrt: höchstens zwei lange Tage
				flexible.put(day, floorToQuarterHour(Math.min(available * plan.volume(),
						Math.max(MIN_CAPPED_RIDE, plan.longRideMinutes() * OTHER_DAYS_SHARE))));
			}
		}
		if (plan.rolling() != null) {
			java.util.Map<LocalDate, Integer> hardByDay = new java.util.HashMap<>();
			for (PlannedWorkout hard : week) {
				hardByDay.merge(hard.getDate(), library.resolve(hard.getWorkoutId(), hard.getDurationMinutes())
						.orElseThrow().getDurationInMinutes(), Integer::sum);
			}
			fitRollingWindow(flexible, longDay, hardByDay, plan.rolling().daily(), plan.rolling().fixed(),
					plan.rolling().weeklyCapMinutes());
		}
		fillHardDays(week, availability, plan, flexible, days.getLast());

		// ein lockerer Tag pro Woche bekommt Einlagen (Antritte oder Tempo), damit nicht jeder Tag gleich aussieht –
		// in der Erholungswoche ohne kurze Qualitätseinheit ein paar Antritte, die die Beine wach halten
		boolean recoverySpice = plan.phase() == Phase.RECOVERY && plan.hardWorkouts().isEmpty();
		boolean spiceWeek = plan.phase() == Phase.BASE || plan.phase() == Phase.BUILD || plan.phase() == Phase.PEAK
				|| recoverySpice;
		boolean spiced = false;
		for (var entry : flexible.entrySet()) {
			LocalDate day = entry.getKey();
			int minutes = entry.getValue();
			if (day.equals(longDay)) {
				// Wochenend-Fahrer: passt keine harte Einheit in die Woche, bekommt die lange Ausfahrt im Aufbau und in
				// der Spitze Tempo-Blöcke – sonst gäbe es über Wochen keinen Reiz oberhalb der Grundlage
				boolean noQuality = hardDays.isEmpty() && !plan.hardWorkouts().isEmpty()
						&& (plan.phase() == Phase.BUILD || plan.phase() == Phase.PEAK);
				week.add(longRide(day, minutes, plan, noQuality));
			} else {
				int available = availableMinutes(availability, day);
				PlannedWorkout easy = easyWorkout(day, minutes, available, plan.phase());
				// der Tag nach einem FTP-Test bleibt ohne Einlagen (der Test ermüdet)
				boolean afterTest = plan.ftpTestDays().contains(day.minusDays(1))
						|| week.stream().anyMatch(planned -> planned.getWorkoutId() != null
								&& planned.getWorkoutId() == WorkoutLibrary.FTP_TEST
								&& planned.getDate().equals(day.minusDays(1)));
				if (spiceWeek && !spiced && !afterTest && !isAfterBigDay(availability, day)
						&& easy.getWorkoutId() == WorkoutLibrary.endurance(60).getId()) {
					int length = easy.getDurationMinutes();
					// reihum: Antritte, Tempo-Einlagen, Tempo-Finale (Abwechslung unter der Woche)
					Workout spice = recoverySpice ? WorkoutLibrary.enduranceWithSprints(length)
							: switch (plan.weekIndex() % 3) {
						case 0 -> WorkoutLibrary.enduranceWithSprints(length);
						case 1 -> WorkoutLibrary.enduranceWithTempo(length);
						default -> WorkoutLibrary.enduranceWithFinish(length);
					};
					easy = new PlannedWorkout(day, spice, length, plan.phase());
					spiced = true;
				}
				week.add(easy);
			}
		}

		week.sort(Comparator.comparing(PlannedWorkout::getDate));
		return week;
	}

	/** Ab so vielen Trainingstagen bekommt eine Erholungswoche einen zusätzlichen freien Tag */
	static final int RECOVERY_REST_MIN_DAYS = 5;

	/** Erst ab so viel Zusatzzeit lohnt sich das Auffüllen eines harten Tags */
	static final int FILL_MIN_EXTRA = 15;
	/** Ab so viel verfügbarer Zeit gilt ein Tag als großer Umfangstag (lange Ausfahrt, lange Grundlage) */
	static final int BIG_DAY_MINUTES = 180;

	/** Ist der Vortag (laut Wochenzeit) ein großer Umfangstag? Gilt auch über die Wochengrenze (So → Mo). */
	static boolean isAfterBigDay(WeeklyAvailability availability, LocalDate day) {
		return availableMinutes(availability, day.minusDays(1)) >= BIG_DAY_MINUTES;
	}

	/**
	 * Wie lang die harte Einheit mit lockerer Grundlage nach dem Hauptteil werden soll (siehe WorkoutFitter):
	 * aerober Umfang, der kaum zusätzlich ermüdet. Im gleichen Verhältnis wie die lockeren Tage (Umfangsfaktor),
	 * nicht in Erholung und Tapering. null = Workout bleibt, wie es ist.
	 */
	static Integer filledLength(Workout workout, int available, WeekPlan plan) {
		if (!isLoadPhase(plan.phase()) || !WorkoutLibrary.isLengthAdjustable(workout)) {
			return null;
		}
		int target = Math.min(available, floorToQuarterHour(available * plan.volume()));
		return target >= workout.getDurationInMinutes() + FILL_MIN_EXTRA ? target : null;
	}

	/**
	 * Füllt die harten Tage auf (siehe filledLength) – erst wenn die lockeren Tage feststehen und nur so weit, wie
	 * jedes 7-Tage-Fenster unter dem Deckel bleibt. Eine harte Einheit wird dabei nie länger als die längste lockere
	 * Fahrt der Woche.
	 */
	private void fillHardDays(List<PlannedWorkout> week, WeeklyAvailability availability, WeekPlan plan,
			java.util.Map<LocalDate, Integer> flexible, LocalDate lastDay) {
		int longest = flexible.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		for (int i = 0; i < week.size(); i++) {
			PlannedWorkout hard = week.get(i);
			if (hard.getWorkoutId() == null || hard.getWorkoutId() >= WorkoutLibrary.CUSTOM_ID_OFFSET) {
				continue;
			}
			Workout workout = library.findById(hard.getWorkoutId()).orElseThrow();
			int current = library.resolve(hard.getWorkoutId(), hard.getDurationMinutes()).orElseThrow()
					.getDurationInMinutes();
			Integer target = filledLength(workout, availableMinutes(availability, hard.getDate()), plan);
			if (target == null) {
				continue;
			}
			int length = Math.min(target, longest);
			if (plan.rolling() != null) {
				java.util.Map<LocalDate, Integer> hardByDay = new java.util.HashMap<>();
				for (PlannedWorkout other : week) {
					hardByDay.merge(other.getDate(), library.resolve(other.getWorkoutId(), other.getDurationMinutes())
							.orElseThrow().getDurationInMinutes(), Integer::sum);
				}
				int slack = Integer.MAX_VALUE;
				for (LocalDate end = hard.getDate(); !end.isAfter(lastDay); end = end.plusDays(1)) {
					slack = Math.min(slack, plan.rolling().weeklyCapMinutes() - windowMinutes(end, flexible, hardByDay,
							plan.rolling().daily(), plan.rolling().fixed()));
				}
				length = Math.min(length, current + Math.max(0, slack));
			}
			length = floorToQuarterHour(length);
			if (length >= current + FILL_MIN_EXTRA) {
				week.set(i, new PlannedWorkout(hard.getDate(), workout, length, plan.phase()));
			}
		}
	}

	/**
	 * Hält jedes 7-Tage-Fenster unter dem Deckel. Die harten Einheiten bleiben. Wird ein Fenster zu voll, werden
	 * zuerst die lockeren Nebentage darin gekürzt (nicht unter {@link #MIN_CAPPED_RIDE}), die lange Ausfahrt ist die
	 * wichtigste Einheit und bleibt, solange es geht. Tage vor dieser Woche (before) sind gefahren oder schon
	 * geplant und werden nicht mehr verändert.
	 */
	private static void fitRollingWindow(java.util.Map<LocalDate, Integer> flexible, LocalDate longDay,
			java.util.Map<LocalDate, Integer> hardByDay, java.util.Map<LocalDate, Integer> before,
			java.util.Map<LocalDate, Integer> fixed, int cap) {
		java.util.Map<LocalDate, Integer> original = new java.util.HashMap<>(flexible);
		List<LocalDate> days = java.util.stream.Stream.concat(flexible.keySet().stream(), hardByDay.keySet().stream())
				.distinct().sorted().toList();
		for (LocalDate day : days) {
			int excess = windowMinutes(day, flexible, hardByDay, before, fixed) - cap;
			if (excess <= 0) {
				continue;
			}
			List<LocalDate> candidates = flexible.keySet().stream()
					.filter(x -> !x.equals(longDay) && !fixed.containsKey(x) && !x.isAfter(day)
							&& !x.isBefore(day.minusDays(6)))
					.sorted(Comparator.comparing((LocalDate x) -> flexible.get(x)).reversed()).toList();
			for (LocalDate candidate : candidates) {
				excess -= cut(flexible, original, candidate, excess);
			}
			if (excess > 0 && flexible.containsKey(longDay) && !longDay.isAfter(day) && !longDay.isBefore(day.minusDays(6))) {
				cut(flexible, original, longDay, excess);
			}
		}
		flexible.replaceAll((day, minutes) -> Math.min(minutes, floorToQuarterHour(minutes) < MIN_CAPPED_RIDE
				? minutes : floorToQuarterHour(minutes)));
	}

	/** Kürzt einen lockeren Tag um höchstens excess, aber nicht unter MIN_CAPPED_RIDE (oder was er ohnehin hatte) */
	private static int cut(java.util.Map<LocalDate, Integer> flexible, java.util.Map<LocalDate, Integer> original,
			LocalDate day, int excess) {
		int floor = Math.min(original.get(day), MIN_CAPPED_RIDE);
		int cut = Math.min(excess, Math.max(0, flexible.get(day) - floor));
		flexible.put(day, flexible.get(day) - cut);
		return cut;
	}

	private static int windowMinutes(LocalDate day, java.util.Map<LocalDate, Integer> flexible,
			java.util.Map<LocalDate, Integer> hardByDay, java.util.Map<LocalDate, Integer> before,
			java.util.Map<LocalDate, Integer> fixed) {
		int sum = 0;
		for (int back = 0; back < 7; back++) {
			LocalDate x = day.minusDays(back);
			if (fixed.containsKey(x)) {
				sum += fixed.get(x);
			} else if (flexible.containsKey(x) || hardByDay.containsKey(x)) {
				sum += flexible.getOrDefault(x, 0) + hardByDay.getOrDefault(x, 0);
			} else {
				sum += before.getOrDefault(x, 0);
			}
		}
		return sum;
	}

	/** Passt das Workout in die Zeit – notfalls mit gekürztem Rahmen (die Intervalle bleiben gleich)? */
	/**
	 * Hat der Tag vor dem FTP-Test Pause? Entweder ist er ohnehin frei (kein Trainingstag, auch am Wochenwechsel: dann
	 * darf er keine Zeit haben), oder er lässt sich freihalten – nur die lange Ausfahrt (und ihr Partner) bleibt.
	 */
	private static boolean hasRestDayBefore(LocalDate candidate, List<LocalDate> days, WeeklyAvailability availability,
			LocalDate longDay, LocalDate partner) {
		LocalDate before = candidate.minusDays(1);
		if (!days.contains(before)) {
			// am Wochenbeginn kennen wir die Woche davor nicht: frei nur, wenn der Vortag keine Zeit hat
			return !before.getDayOfWeek().equals(DayOfWeek.SUNDAY) || availableMinutes(availability, before) == 0;
		}
		return !before.equals(longDay) && !before.equals(partner);
	}

	private static boolean fitsInto(Workout workout, int availableMinutes) {
		return workout.getDurationInMinutes() <= availableMinutes
				|| (WorkoutLibrary.isLengthAdjustable(workout) && WorkoutFitter.minimumMinutes(workout) <= availableMinutes);
	}

	/** Dieselben Workouts, beginnend bei Nummer offset (reihum) */
	private static List<Workout> rotated(List<Workout> workouts, int offset) {
		if (workouts.isEmpty()) {
			return workouts;
		}
		List<Workout> result = new ArrayList<>(workouts);
		java.util.Collections.rotate(result, -Math.floorMod(offset, workouts.size()));
		return result;
	}

	private PlannedWorkout longRide(LocalDate day, int targetMinutes, WeekPlan plan, boolean forceTempo) {
		int minutes = floorToQuarterHour(targetMinutes);
		if (minutes < 150) {
			return easyWorkout(day, minutes, targetMinutes, plan.phase());
		}
		if ((plan.tempoLongRide() || forceTempo) && plan.phase() != Phase.RECOVERY
				&& minutes >= (forceTempo ? 150 : 180)) {
			return new PlannedWorkout(day, WorkoutLibrary.longRideWithTempo(minutes), minutes, plan.phase());
		}
		// Ultra und lange Events: jede zweite lange Ausfahrt (ohne Tempo-Blöcke) wird in der letzten Stunde
		// progressiv schneller – Ermüdungsresistenz, ohne hart zu sein
		boolean longEvent = plan.focus() == EventProfile.Focus.ULTRA || plan.focus() == EventProfile.Focus.LONG_FLAT
				|| plan.focus() == EventProfile.Focus.LONG_CLIMBING;
		if (longEvent && minutes >= PROGRESSIVE_LONG_RIDE_MIN_MINUTES && plan.weekIndex() % 2 == 1
				&& (plan.phase() == Phase.BASE || plan.phase() == Phase.BUILD)) {
			return new PlannedWorkout(day, WorkoutLibrary.longRideProgressive(minutes), minutes, plan.phase());
		}
		return new PlannedWorkout(day, WorkoutLibrary.longRide(minutes), minutes, plan.phase());
	}

	/** Jede so-vielte Woche stellt der Plan eine passende Spezialeinheit vor die normale Auswahl */
	static final int SPECIALTY_EVERY_NTH_WEEK = 3;

	/** Eine progressive lange Ausfahrt braucht mindestens so viel Zeit (eine Stunde Steigerung plus Rahmen) */
	static final int PROGRESSIVE_LONG_RIDE_MIN_MINUTES = 210;

	/** Grundlage ab einer Stunde, sonst lockeres Ausrollen (mindestens 30 min, nie mehr als verfügbar) */
	private PlannedWorkout easyWorkout(LocalDate day, int minutes, int available, Phase phase) {
		// Erholungswoche: schon ab 45 min Grundlage statt Ausrollen – sonst fällt die Belastung (TSS) um über 70 %,
		// gewünscht sind etwa 30 bis 50 %; weniger Umfang, aber nicht nur Regenerationstempo
		if (minutes >= (phase == Phase.RECOVERY ? RECOVERY_ENDURANCE_MIN_MINUTES : 60)) {
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

		// die knackige Einheit mit Abstand zum Event (spätestens 3 Tage vorher), möglichst nicht am Tag nach einem
		// großen Umfangstag
		LocalDate sharpenerDay = days.stream()
				.filter(day -> day.isBefore(eventDate.minusDays(2))
						&& availableMinutes(availability, day) >= sharpener.getDurationInMinutes())
				.min(Comparator.comparing((LocalDate day) -> isAfterBigDay(availability, day))
						.thenComparing(Comparator.naturalOrder()))
				.orElse(null);
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
			if (day.equals(sharpenerDay)) {
				week.add(new PlannedWorkout(day, sharpener, null, Phase.TAPER));
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

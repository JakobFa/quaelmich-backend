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
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
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

	private static final Set<WorkoutType> TOO_HARD_FOR_BEGINNERS = Set.of(WorkoutType.ANAEROBIC,
			WorkoutType.SPRINT, WorkoutType.OVER_UNDER);

	private final WorkoutLibrary library;

	public PlanGenerator(WorkoutLibrary library) {
		this.library = library;
	}

	public List<PlannedWorkout> generate(Athlete athlete, LocalDate startDate) {
		LocalDate eventDate = athlete.getEvent().getDate();
		LocalDate firstMonday = startDate.with(previousOrSame(DayOfWeek.MONDAY));
		LocalDate eventMonday = eventDate.with(previousOrSame(DayOfWeek.MONDAY));
		int allWeeks = (int) ChronoUnit.WEEKS.between(firstMonday, eventMonday) + 1;
		if (allWeeks < 1) {
			return List.of();
		}

		EventProfile profile = EventProfile.of(athlete);
		List<Phase> phases = phasesFor(allWeeks);
		int loadWeeksTotal = (int) phases.stream().filter(PlanGenerator::isLoadPhase).count();
		int ftpRetestWeek = firstBuildLoadWeek(phases);
		double weeklyHours = athlete.getAvailability().totalHours();

		// Bei sehr weit entfernten Events nur das erste Jahr planen
		int weeks = Math.min(allWeeks, MAX_WEEKS);
		List<PlannedWorkout> plan = new ArrayList<>();
		Map<Phase, Integer> weeksInPhase = new EnumMap<>(Phase.class);
		int loadWeek = 0;
		for (int weekIndex = 0; weekIndex < weeks; weekIndex++) {
			LocalDate monday = firstMonday.plusWeeks(weekIndex);
			Phase phase = phases.get(weekIndex);
			int weeksUntilEvent = allWeeks - 1 - weekIndex;

			List<LocalDate> trainingDays = monday.datesUntil(monday.plusWeeks(1))
					.filter(day -> !day.isBefore(startDate) && day.isBefore(eventDate))
					.filter(day -> availableMinutes(athlete, day) > 0)
					.toList();

			if (weeksUntilEvent == 0) {
				plan.addAll(planEventWeek(athlete, trainingDays, eventDate, profile));
				continue;
			}

			int step = progressionStep(athlete.getLevel(), phase, weeksInPhase.getOrDefault(phase, 0));
			weeksInPhase.merge(phase, 1, Integer::sum);

			WeekPlan week = new WeekPlan(phase, weekIndex, step,
					volumeFactor(phase, loadWeek),
					longRideMinutes(profile, phase, loadWeek, loadWeeksTotal),
					useTempoLongRide(profile, phase, loadWeek),
					hardWorkoutsFor(athlete.getLevel(), profile.focus(), phase, weekIndex, weeksUntilEvent,
							weeklyHours, weekIndex == 0 && allWeeks >= 6 || weekIndex == ftpRetestWeek));
			plan.addAll(planWeek(athlete, trainingDays, week));

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
	private static int firstBuildLoadWeek(List<Phase> phases) {
		return phases.indexOf(Phase.BUILD) > 0 ? phases.indexOf(Phase.BUILD) : -1;
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

	// ---------- Umfang ----------

	/** Anteil der verfügbaren Zeit, der für lockere Fahrten genutzt wird */
	static double volumeFactor(Phase phase, int loadWeek) {
		double ramp = Math.min(1.0, START_VOLUME + VOLUME_STEP * loadWeek);
		return switch (phase) {
			case RECOVERY -> 0.6 * ramp; // Erholung: deutlich weniger als die Wochen davor
			case TAPER -> 0.6;
			case PEAK -> 1.0;
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
		boolean longEvent = profile.focus() == Focus.LONG_CLIMBING || profile.focus() == Focus.LONG_FLAT;
		return longEvent && (phase == Phase.BUILD || phase == Phase.PEAK) && loadWeek % 2 == 0;
	}

	// ---------- harte Workouts ----------

	/** Harte Einheiten pro Woche nach Level und Wochenzeit */
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
		if (phase == Phase.RECOVERY) {
			return List.of();
		}
		if (phase == Phase.TAPER) {
			// Woche vor dem Event: eine kurze, harte Einheit passend zum Event
			return List.of(focus == Focus.SHORT_INTENSE || focus == Focus.MIXED ? WorkoutType.VO2MAX
					: WorkoutType.THRESHOLD);
		}

		List<WorkoutType> pool = poolFor(focus, phase, level, weeklyHours);
		int count = hardSessionsPerWeek(level, weeklyHours);
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

	private List<PlannedWorkout> planWeek(Athlete athlete, List<LocalDate> days, WeekPlan plan) {
		if (days.isEmpty()) {
			return List.of();
		}

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

		for (WorkoutType type : plan.hardWorkouts()) {
			// passt die geplante Stufe nicht in die Zeit, wird es die nächstleichtere Variante
			for (int step = plan.step(); step >= 0; step--) {
				Workout workout = library.variant(type, step);
				var day = candidates.stream()
						.filter(candidate -> !hardDays.contains(candidate))
						.filter(candidate -> hardDays.stream()
								.noneMatch(hard -> Math.abs(ChronoUnit.DAYS.between(hard, candidate)) == 1))
						.filter(candidate -> availableMinutes(athlete, candidate) >= workout.getDurationInMinutes())
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
			int available = availableMinutes(athlete, day);
			if (day.equals(longDay)) {
				week.add(longRide(day, Math.min(plan.longRideMinutes(), available), plan));
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
	private List<PlannedWorkout> planEventWeek(Athlete athlete, List<LocalDate> days, LocalDate eventDate,
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
			int available = availableMinutes(athlete, day);
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

	private static int availableMinutes(Athlete athlete, LocalDate day) {
		return (int) Math.round(athlete.getAvailability().hoursOn(day.getDayOfWeek()) * 60);
	}

	/** Abrunden, damit nie mehr Zeit geplant wird, als verfügbar ist */
	private static int floorToQuarterHour(double minutes) {
		return (int) (Math.floor(minutes / 15) * 15);
	}

}

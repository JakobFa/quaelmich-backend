package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/**
 * Fitness, Ermüdung und Form aus der Belastung (TSS) – das übliche Modell aus TrainingPeaks & Co.:
 * Fitness = gleitender Schnitt über 42 Tage, Ermüdung über 7 Tage, Form = Fitness − Ermüdung des Vortags.
 * Bis heute zählt, was gefahren wurde; ab morgen (und heute, solange nichts gefahren ist) das Geplante –
 * so sieht man, mit welcher Form man voraussichtlich zum Event kommt.
 */
@Service
public class FormService {

	static final int FITNESS_DAYS = 42;
	static final int FATIGUE_DAYS = 7;

	/** Ein Tag der Kurve; planned = Zukunft, nur aus dem Plan berechnet */
	public record Day(LocalDate date, int tss, double fitness, double fatigue, double form, boolean planned) {
	}

	/** eventForm = voraussichtliche Form am Eventtag (null, wenn das Event schon vorbei ist) */
	public record FormCurve(List<Day> days, Day today, Double eventForm, LocalDate eventDate) {
	}

	private final AthleteService athleteService;
	private final TrainingPlanRepository plans;
	private final ExtraRideRepository extraRides;
	private final WorkoutLibrary library;
	private final Clock clock;

	public FormService(AthleteService athleteService, TrainingPlanRepository plans, ExtraRideRepository extraRides,
			WorkoutLibrary library, Clock clock) {
		this.athleteService = athleteService;
		this.plans = plans;
		this.extraRides = extraRides;
		this.library = library;
		this.clock = clock;
	}

	/** Leer ohne Profil oder Plan */
	@Transactional(readOnly = true)
	public Optional<FormCurve> curveOf(Long userId) {
		return athleteService.findByOwner(userId).flatMap(athlete -> plans.findByAthleteId(athlete.getId())
				.map(plan -> curve(plan.getWorkouts(), extraRides.findByAthleteId(athlete.getId()),
						LocalDate.now(clock), athlete.getEvent().getDate())));
	}

	FormCurve curve(List<PlannedWorkout> workouts, List<ExtraRide> rides, LocalDate today, LocalDate eventDate) {
		Map<LocalDate, Integer> actual = new TreeMap<>();
		Map<LocalDate, Integer> planned = new TreeMap<>();
		for (PlannedWorkout workout : workouts) {
			int plannedTss = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes())
					.map(w -> w.getTss()).orElse(0);
			if (workout.getActualTss() != null) {
				actual.merge(workout.getDate(), workout.getActualTss(), Integer::sum);
			} else if (workout.isCompleted()) {
				actual.merge(workout.getDate(), plannedTss, Integer::sum);
			} else if (!workout.getDate().isBefore(today) && workout.getSkipReason() == null) {
				planned.merge(workout.getDate(), plannedTss, Integer::sum);
			}
		}
		for (ExtraRide ride : rides) {
			actual.merge(ride.getDate(), ProgressService.xpOf(ride), Integer::sum);
		}

		LocalDate start = java.util.stream.Stream.concat(actual.keySet().stream(), planned.keySet().stream())
				.min(LocalDate::compareTo).orElse(today);
		LocalDate end = eventDate.isAfter(today) ? eventDate : today;

		List<Day> days = new ArrayList<>();
		double fitness = 0;
		double fatigue = 0;
		Day todayDay = null;
		Double eventForm = null;
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			double form = fitness - fatigue; // Form des Tages = Stand vom Vortag
			boolean future = date.isAfter(today) || (date.equals(today) && !actual.containsKey(today));
			int tss = actual.getOrDefault(date, 0) + (future ? planned.getOrDefault(date, 0) : 0);
			fitness += (tss - fitness) / FITNESS_DAYS;
			fatigue += (tss - fatigue) / FATIGUE_DAYS;
			Day day = new Day(date, tss, round(fitness), round(fatigue), round(form), future && !date.equals(today));
			days.add(day);
			if (date.equals(today)) {
				todayDay = day;
			}
			if (date.equals(eventDate)) {
				eventForm = round(form);
			}
		}
		return new FormCurve(days, todayDay, eventDate.isBefore(today) ? null : eventForm, eventDate);
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

}

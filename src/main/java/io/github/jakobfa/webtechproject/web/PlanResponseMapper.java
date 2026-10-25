package io.github.jakobfa.webtechproject.web;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.service.EventProfile;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;
import io.github.jakobfa.webtechproject.web.dto.ExtraRideResponse;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

/**
 * Wandelt Plan-Entities in die Antworten fürs Frontend um und ergänzt die Workout-Daten aus der Bibliothek.
 */
@Component
public class PlanResponseMapper {

	private final WorkoutLibrary workoutLibrary;
	private final PlanAdaptationService adaptation;
	private final ExtraRideRepository extraRideRepository;
	private final java.time.Clock clock;
	private final io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents;
	private final WorkoutCustomizationService customization;

	public PlanResponseMapper(WorkoutLibrary workoutLibrary, PlanAdaptationService adaptation,
			ExtraRideRepository extraRideRepository, WorkoutCustomizationService customization, io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents, java.time.Clock clock) {
		this.clock = clock;
		this.raceEvents = raceEvents;
		this.workoutLibrary = workoutLibrary;
		this.adaptation = adaptation;
		this.extraRideRepository = extraRideRepository;
		this.customization = customization;
	}

	public TrainingPlanResponse toResponse(TrainingPlan plan) {
		var event = plan.getAthlete().getEvent();
		return new TrainingPlanResponse(plan.getId(), plan.getStartDate(), event.getName(), event.getDate(),
				plan.getWorkouts().stream().map(this::toResponse).toList(),
				extraRideRepository
						// ab Montag der ersten Planwoche – so bleiben Fahrten sichtbar, auch wenn der Plan
						// mitten in der Woche neu erstellt wurde
						.findByAthleteIdAndDateGreaterThanEqualOrderByDate(plan.getAthlete().getId(),
								plan.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
						.stream().map(PlanResponseMapper::toResponse).toList(),
				// Schwerpunkt des nächsten anstehenden Events
				EventProfile.of(EventProfile.nextEvent(event, moreEventsOf(plan), LocalDate.now(clock)),
						plan.getAthlete().getLevel()),
				// Kopie, damit die Antwort nicht an der Hibernate-Collection hängt
				new java.util.TreeMap<>(plan.getWeekAvailability()),
				raceEvents.findByAthleteIdOrderByDate(plan.getAthlete().getId()));
	}

	public static ExtraRideResponse toResponse(ExtraRide ride) {
		return new ExtraRideResponse(ride.getId(), ride.getDate(), ride.getDurationMinutes(), ride.getDistanceKm(),
				ride.getTss(), ride.assessment(), RecordedActivity.stravaUrl(ride.getActivityId()),
				ride.isTssFromHeartRate(), ride.isTssFromDuration(), ride.getSport());
	}

	public PlannedWorkoutResponse toResponse(PlannedWorkout planned) {
		var workout = workoutLibrary.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		var actual = planned.getActivityId() == null ? null
				: new PlannedWorkoutResponse.Actual(planned.getActualDurationMinutes(), planned.getActualDistanceKm(),
						planned.getActualTss(), adaptation.assess(planned), planned.getActivityConfirmed(),
						RecordedActivity.stravaUrl(planned.getActivityId()), planned.isActualTssFromHeartRate());
		var adjustment = planned.getAdjustmentNote() == null ? null
				: new PlannedWorkoutResponse.Adjustment(planned.getAdjustmentNote(), planned.getOriginalTitle());
		return new PlannedWorkoutResponse(planned.getId(), planned.getDate(), planned.getPhase(),
				planned.isCompleted(), planned.getRating(), planned.getSkipReason(), workout, actual, adjustment,
				customization.optionsFor(planned));
	}

	private java.util.List<io.github.jakobfa.webtechproject.model.Event> moreEventsOf(TrainingPlan plan) {
		return raceEvents.findByAthleteIdOrderByDate(plan.getAthlete().getId()).stream()
				.map(io.github.jakobfa.webtechproject.model.RaceEvent::toEvent).toList();
	}

}

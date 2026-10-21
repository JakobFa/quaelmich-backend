package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.jakobfa.webtechproject.service.EventProfile;

/**
 * Der Trainingsplan, wie er ans Frontend geht.
 */
public record TrainingPlanResponse(Long id, LocalDate startDate, String eventName, LocalDate eventDate,
		List<PlannedWorkoutResponse> workouts, List<ExtraRideResponse> extraRides, EventProfile eventProfile) {
}

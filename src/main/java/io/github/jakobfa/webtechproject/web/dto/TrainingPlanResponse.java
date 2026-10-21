package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Der Trainingsplan, wie er ans Frontend geht.
 */
public record TrainingPlanResponse(Long id, LocalDate startDate, String eventName, LocalDate eventDate,
		List<PlannedWorkoutResponse> workouts, List<ExtraRideResponse> extraRides) {
}

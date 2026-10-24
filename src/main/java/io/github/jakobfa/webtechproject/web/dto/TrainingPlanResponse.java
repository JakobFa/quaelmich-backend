package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;

import io.github.jakobfa.webtechproject.service.EventProfile;

/**
 * Der Trainingsplan, wie er ans Frontend geht.
 */
public record TrainingPlanResponse(Long id, LocalDate startDate, String eventName, LocalDate eventDate,
		List<PlannedWorkoutResponse> workouts, List<ExtraRideResponse> extraRides, EventProfile eventProfile,
		Map<LocalDate, WeeklyAvailability> weekAvailability, List<RaceEvent> moreEvents) {
}

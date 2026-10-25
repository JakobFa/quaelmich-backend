package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.model.TrainingGoal;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;

import io.github.jakobfa.webtechproject.service.EventProfile;

/**
 * Der Trainingsplan, wie er ans Frontend geht. Bei einem Ziel ohne Event (trainingGoal ≠ EVENT) sind
 * eventName und eventDate leer; eventProfile beschreibt dann den Schwerpunkt des Ziels.
 */
public record TrainingPlanResponse(Long id, LocalDate startDate, String eventName, LocalDate eventDate,
		List<PlannedWorkoutResponse> workouts, List<ExtraRideResponse> extraRides, EventProfile eventProfile,
		Map<LocalDate, WeeklyAvailability> weekAvailability, List<RaceEvent> moreEvents,
		TrainingGoal trainingGoal) {
}

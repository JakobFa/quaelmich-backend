package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;

import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.Sport;

/** Einheit ohne geplantes Workout (Rad oder andere Sportart); assessment ist null bei einer lockeren Runde, stravaUrl nur bei Strava-Fahrten */
public record ExtraRideResponse(Long id, LocalDate date, Integer durationMinutes, Double distanceKm, Integer tss,
		Assessment assessment, String stravaUrl, boolean tssFromHeartRate, boolean tssFromDuration, Sport sport) {
}

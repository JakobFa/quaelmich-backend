package io.github.jakobfa.webtechproject.integration.intervals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ein Tag aus dem Wellness-Bereich von intervals.icu. Dort landen Schlaf und Erholung aus allen angebundenen
 * Quellen (Garmin, Oura, Whoop, Apple Health über Zusatz-Apps, eigene Einträge …). id ist das Datum (yyyy-MM-dd).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntervalsWellness(String id, Integer sleepSecs, Double sleepScore, Integer restingHR, Double hrv,
		Double hrvSDNN, Integer steps) {
}

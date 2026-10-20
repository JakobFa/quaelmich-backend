package io.github.jakobfa.webtechproject.integration.intervals;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ein geplantes Workout im Kalender-Format der intervals.icu-API (Felder in snake_case).
 */
public record IntervalsEvent(
		String category,
		@JsonProperty("start_date_local") String startDateLocal,
		String type,
		String name,
		String description,
		String filename,
		@JsonProperty("file_contents_base64") String fileContentsBase64,
		@JsonProperty("external_id") String externalId) {
}

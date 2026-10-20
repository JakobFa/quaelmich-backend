package io.github.jakobfa.webtechproject.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Fortschritt eines geplanten Workouts: erledigt ja/nein und gefühlte Anstrengung von 1 bis 10.
 */
public record WorkoutProgressRequest(boolean completed, @Min(1) @Max(10) Integer rating) {
}

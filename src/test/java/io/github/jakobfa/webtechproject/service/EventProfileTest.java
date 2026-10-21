package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.service.EventProfile.Focus;

class EventProfileTest {

	private static Athlete athlete(Level level, double km, int meters, EventType type) {
		Event event = new Event();
		event.setDate(LocalDate.of(2027, 7, 1));
		event.setDistanceKm(km);
		event.setElevationMeters(meters);
		event.setType(type);
		Athlete athlete = new Athlete();
		athlete.setLevel(level);
		athlete.setEvent(event);
		return athlete;
	}

	@Test
	void estimatesDurationFromDistanceClimbingAndLevel() {
		var oetztaler = EventProfile.of(athlete(Level.AMBITIOUS, 227, 5500, EventType.GRAN_FONDO));
		assertThat(oetztaler.estimatedMinutes()).isBetween(8 * 60, 11 * 60);
		assertThat(oetztaler.focus()).isEqualTo(Focus.LONG_CLIMBING);

		var beginner = EventProfile.of(athlete(Level.BEGINNER, 227, 5500, EventType.GRAN_FONDO));
		assertThat(beginner.estimatedMinutes()).isGreaterThan(oetztaler.estimatedMinutes());
	}

	@Test
	void eventTypeDecidesTheFocus() {
		assertThat(EventProfile.of(athlete(Level.HOBBY, 40, 100, EventType.CRITERIUM)).focus())
				.isEqualTo(Focus.SHORT_INTENSE);
		assertThat(EventProfile.of(athlete(Level.HOBBY, 40, 300, EventType.TIME_TRIAL)).focus())
				.isEqualTo(Focus.TIME_TRIAL);
		assertThat(EventProfile.of(athlete(Level.HOBBY, 600, 6000, EventType.ULTRA)).focus()).isEqualTo(Focus.ULTRA);
		assertThat(EventProfile.of(athlete(Level.HOBBY, 120, 1500, EventType.ROAD_RACE)).focus())
				.isEqualTo(Focus.MIXED);
	}

	@Test
	void withoutTypeTheFocusComesFromDurationAndClimbing() {
		assertThat(EventProfile.focusFor(null, 6, 4)).isEqualTo(Focus.LONG_FLAT);
		assertThat(EventProfile.focusFor(null, 6, 15)).isEqualTo(Focus.LONG_CLIMBING);
		assertThat(EventProfile.focusFor(null, 3, 8)).isEqualTo(Focus.MIXED);
		assertThat(EventProfile.focusFor(null, 1.5, 3)).isEqualTo(Focus.SHORT_INTENSE);
		assertThat(EventProfile.focusFor(null, 14, 5)).isEqualTo(Focus.ULTRA);
	}

}

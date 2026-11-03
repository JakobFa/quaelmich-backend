package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StravaActivityNamesTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"Radfahrt am Abend", "Radfahrt am Morgen", "Radfahrt am Mittag", "Fahrt am Nachmittag",
			"Virtuelle Radfahrt am Abend", "Gravel-Fahrt am Vormittag", "Radfahrt in der Nacht",
			"Morning Ride", "Evening Virtual Ride", "Afternoon Gravel Ride", "Lunch Ride", "Night Ride",
			"  Radfahrt am Abend  "})
	@NullAndEmptySource
	void recognisesStravaDefaultNames(String name) {
		assertThat(StravaActivityNames.isDefault(name)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Become the  Base Layer Slayer", "Schaltzug kann reißen, aber nicht meine Strava streak",
			"Tempo 2 × 15", "Kill you ego or Break your light", "Radfahrt am Abend mit Anne",
			"Berg am Abend wieder hoch", "Evening Ride with friends", "Grundlage 4:30 h"})
	void keepsEveryTitleTheUserWroteHimself(String name) {
		assertThat(StravaActivityNames.isDefault(name)).isFalse();
	}
}

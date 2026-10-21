package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SportTest {

	@Test
	void mapsActivityTypesOfStravaAndIntervals() {
		assertThat(Sport.fromActivityType("VirtualRide")).isEqualTo(Sport.RIDE);
		assertThat(Sport.fromActivityType("TrailRun")).isEqualTo(Sport.RUN);
		assertThat(Sport.fromActivityType("Swim")).isEqualTo(Sport.SWIM);
		assertThat(Sport.fromActivityType("WeightTraining")).isEqualTo(Sport.STRENGTH);
		assertThat(Sport.fromActivityType("Hike")).isEqualTo(Sport.HIKE);
		assertThat(Sport.fromActivityType("Rowing")).isEqualTo(Sport.OTHER);
		assertThat(Sport.fromActivityType(null)).isEqualTo(Sport.OTHER);
	}

	@Test
	void estimatesLoadPerHourExceptForRides() {
		assertThat(Sport.RUN.estimateTss(1800)).isEqualTo(30);
		assertThat(Sport.SWIM.estimateTss(3600)).isEqualTo(45);
		assertThat(Sport.WALK.estimateTss(3600)).isEqualTo(20);
		assertThat(Sport.RIDE.estimateTss(3600)).isNull();
	}

}

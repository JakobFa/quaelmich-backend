package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.integration.strava.StravaClient.HeartRateZones;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.ZoneRange;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.Zones;

class StravaZonesTest {

	private static Zones zones(boolean custom, int... starts) {
		var ranges = new java.util.ArrayList<ZoneRange>();
		ranges.add(new ZoneRange(0, starts[0]));
		for (int i = 0; i < starts.length; i++) {
			ranges.add(new ZoneRange(starts[i], i + 1 < starts.length ? starts[i + 1] : -1));
		}
		return new Zones(new HeartRateZones(custom, List.copyOf(ranges)));
	}

	@Test
	void recoversMaxHeartRateFromStravaDefaultZones() {
		assertThat(StravaService.maxHeartRateFromZones(zones(false, 123, 153, 168, 183))).isEqualTo(189);
		assertThat(StravaService.maxHeartRateFromZones(zones(false, 130, 162, 178, 194))).isEqualTo(200);
	}

	@Test
	void takesZoneStartsAlsoFromCustomZones() {
		assertThat(StravaService.zoneStarts(zones(true, 120, 150, 165, 178))).containsExactly(120, 150, 165, 178);
		assertThat(StravaService.zoneStarts(new Zones(null))).isNull();
	}

	@Test
	void customZonesOrMissingZonesGiveNothing() {
		assertThat(StravaService.maxHeartRateFromZones(zones(true, 123, 153, 168, 183))).isNull();
		assertThat(StravaService.maxHeartRateFromZones(new Zones(null))).isNull();
		assertThat(StravaService.maxHeartRateFromZones(null)).isNull();
	}

}

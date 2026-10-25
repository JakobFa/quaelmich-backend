package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Sport;

class FtpSuggestionsTest {

	private static RecordedActivity ride(int minutes, Integer normalizedPower) {
		return new RecordedActivity("i1", Sport.RIDE, LocalDateTime.of(2026, 10, 24, 11, 0), minutes * 60, 40000.0,
				80, false, false, false, normalizedPower, false);
	}

	private static Athlete athleteWithFtp(int ftp) {
		Athlete athlete = new Athlete();
		athlete.setFtp(ftp);
		return athlete;
	}

	@Test
	void anHourWellAboveFtpMeansTheFtpIsTooLow() {
		Athlete athlete = athleteWithFtp(240);

		FtpSuggestions.evaluate(athlete, List.of(ride(75, 272)));

		assertThat(athlete.getSuggestedFtp()).isEqualTo(272);
		assertThat(athlete.getSuggestedFtpNote()).contains("75 min", "272 W");
	}

	@Test
	void smallDifferencesAreNormalDayToDayForm() {
		Athlete athlete = athleteWithFtp(240);

		FtpSuggestions.evaluate(athlete, List.of(ride(90, 255), ride(30, 320)));

		assertThat(athlete.getSuggestedFtp()).as("+6 % bzw. zu kurz").isNull();
	}

	@Test
	void shorterEffortsCountOnlyWithAMargin() {
		Athlete athlete = athleteWithFtp(240);

		FtpSuggestions.evaluate(athlete, List.of(ride(45, 280)));

		assertThat(athlete.getSuggestedFtp()).isEqualTo(266); // 280 × 0,95
	}

	@Test
	void noPowerMeterNoSuggestionAndUpdatedFtpClearsIt() {
		Athlete athlete = athleteWithFtp(240);
		FtpSuggestions.evaluate(athlete, List.of(ride(120, null)));
		assertThat(athlete.getSuggestedFtp()).isNull();

		FtpSuggestions.evaluate(athlete, List.of(ride(70, 280)));
		athlete.setFtp(280);
		FtpSuggestions.evaluate(athlete, List.of());
		assertThat(athlete.getSuggestedFtp()).isNull();
	}

}

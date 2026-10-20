package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssessmentTest {

	@Test
	void comparesTrainingLoad() {
		assertThat(Assessment.of(100, 79, 60, 60)).isEqualTo(Assessment.EASIER);
		assertThat(Assessment.of(100, 80, 60, 60)).isEqualTo(Assessment.AS_PLANNED);
		assertThat(Assessment.of(100, 120, 60, 60)).isEqualTo(Assessment.AS_PLANNED);
		assertThat(Assessment.of(100, 121, 60, 60)).isEqualTo(Assessment.HARDER);
		assertThat(Assessment.of(100, 150, 60, 60)).isEqualTo(Assessment.HARDER);
		assertThat(Assessment.of(100, 151, 60, 60)).isEqualTo(Assessment.MUCH_HARDER);
	}

	@Test
	void fallsBackToDurationWithoutTss() {
		assertThat(Assessment.of(100, null, 60, 100)).isEqualTo(Assessment.MUCH_HARDER);
		assertThat(Assessment.of(100, null, 60, 40)).isEqualTo(Assessment.EASIER);
		assertThat(Assessment.of(100, null, 60, null)).isEqualTo(Assessment.AS_PLANNED);
	}

}

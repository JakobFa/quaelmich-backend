package io.github.jakobfa.webtechproject.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.health.WellnessImportService.Block;

/** Der Rückimport fragt intervals.icu in Blöcken ab: lückenlos, ohne Überlappung, der neueste zuerst */
class WellnessBlocksTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

	private static long days(Block block) {
		return ChronoUnit.DAYS.between(block.oldest(), block.newest()) + 1;
	}

	@Test
	void aYearBecomesFourFullBlocksAndTheRest() {
		LocalDate oldest = TODAY.minusDays(WellnessImportService.WELLNESS_BACKFILL_DAYS - 1L);
		List<Block> blocks = WellnessImportService.blocks(oldest, TODAY, 90);

		assertThat(blocks).extracting(WellnessBlocksTest::days).containsExactly(90L, 90L, 90L, 90L, 5L);
		assertThat(blocks.getFirst().newest()).isEqualTo(TODAY);
		assertThat(blocks.getFirst().oldest()).isEqualTo(TODAY.minusDays(89));
		assertThat(blocks.getLast().oldest()).isEqualTo(oldest);
		for (int i = 1; i < blocks.size(); i++) {
			assertThat(blocks.get(i).newest()).isEqualTo(blocks.get(i - 1).oldest().minusDays(1));
		}
	}

	@Test
	void edgesFitExactly() {
		assertThat(WellnessImportService.blocks(TODAY.minusDays(179), TODAY, 90)).extracting(WellnessBlocksTest::days)
				.containsExactly(90L, 90L);
		assertThat(WellnessImportService.blocks(TODAY, TODAY, 90)).containsExactly(new Block(TODAY, TODAY));
		assertThat(WellnessImportService.blocks(TODAY.plusDays(1), TODAY, 90)).isEmpty();
	}

}

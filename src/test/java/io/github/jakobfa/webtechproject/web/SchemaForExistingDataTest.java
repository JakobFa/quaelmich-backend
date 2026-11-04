package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Neue Spalten am Plan müssen nullable sein: "ddl-auto=update" legt eine Pflichtspalte (NOT NULL ohne Vorgabewert)
 * in einer Tabelle mit bestehenden Zeilen nicht an. Dann fehlt die Spalte, und der Plan lädt für alle nicht mehr –
 * so geschehen beim Krank-Knopf (reentry_weeks als int).
 */
@SpringBootTest
class SchemaForExistingDataTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void everyColumnOfTheTrainingPlanThatIsNewSinceTheFirstReleaseCanBeNull() {
		List<String> notNullable = jdbc.queryForList("""
				select lower(column_name) from information_schema.columns
				where lower(table_name) = 'training_plan' and is_nullable = 'NO'
				""", String.class);

		// nur der Schlüssel, die Planverbindung und das Startdatum gab es von Anfang an
		assertThat(notNullable).doesNotContain("reentry_weeks", "sick_since", "reentry_monday",
				"last_adapted_monday", "calendar_token", "actuals_fingerprint");
	}
}

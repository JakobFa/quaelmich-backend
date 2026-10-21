package io.github.jakobfa.webtechproject.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Kleine Anpassungen am bestehenden Datenbankschema, die "ddl-auto=update" nicht selbst macht
 * (es legt nur neue Tabellen und Spalten an, lockert aber keine Regeln).
 */
@Component
public class SchemaUpdates implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchemaUpdates.class);

	private final JdbcTemplate jdbc;

	public SchemaUpdates(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		// FTP ist seit dem Puls-Modus optional (Training ohne Wattmesser)
		execute("ALTER TABLE athlete ALTER COLUMN ftp DROP NOT NULL");
	}

	private void execute(String sql) {
		try {
			jdbc.execute(sql);
		} catch (RuntimeException e) {
			log.warn("Schema-Anpassung übersprungen ({}): {}", sql, e.getMessage());
		}
	}

}

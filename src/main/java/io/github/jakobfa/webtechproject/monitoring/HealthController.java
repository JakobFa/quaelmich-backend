package io.github.jakobfa.webtechproject.monitoring;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Für die Überwachung: läuft das Backend und antwortet die Datenbank? 503, wenn nicht. Keine Details nach außen. */
@RestController
public class HealthController {

	private final JdbcTemplate jdbc;

	public HealthController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping(path = "/health")
	public ResponseEntity<Map<String, String>> health() {
		try {
			jdbc.queryForObject("select 1", Integer.class);
			return ResponseEntity.ok(Map.of("status", "UP"));
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
		}
	}

}

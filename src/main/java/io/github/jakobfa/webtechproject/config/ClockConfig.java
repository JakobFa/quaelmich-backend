package io.github.jakobfa.webtechproject.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt die Uhr als Bean bereit, damit "heute" in Tests festgelegt werden kann.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Europe/Berlin"));
	}

}

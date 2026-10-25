package io.github.jakobfa.webtechproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Erlaubt @Async – z. B. damit das Schreiben nach Strava das Abhaken nicht ausbremst */
@Configuration
@EnableAsync
public class AsyncConfig {
}

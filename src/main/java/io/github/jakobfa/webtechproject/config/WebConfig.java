package io.github.jakobfa.webtechproject.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Zentrale CORS-Konfiguration für alle Routen (statt @CrossOrigin an jedem Controller).
 * Die erlaubten Frontend-Adressen stehen in application.properties.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedMethods("GET", "POST", "PUT", "DELETE")
				.allowedOrigins(allowedOrigins)
				// damit das Frontend beim .fit-Download den Dateinamen lesen darf
				.exposedHeaders("Content-Disposition");
	}

}

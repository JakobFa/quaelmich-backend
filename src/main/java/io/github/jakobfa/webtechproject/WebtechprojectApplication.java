package io.github.jakobfa.webtechproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // stündliche Fehler-Mail (ErrorReporter)
public class WebtechprojectApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebtechprojectApplication.class, args);
	}

}

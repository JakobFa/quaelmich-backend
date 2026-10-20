package io.github.jakobfa.webtechproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WebtechprojectApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebtechprojectApplication.class, args);
	}

}

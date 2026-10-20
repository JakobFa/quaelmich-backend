package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Hilfsmethoden für Tests: Nutzer registrieren, Profil und Plan anlegen.
 */
final class TestUsers {

	private TestUsers() {
	}

	/** Registriert einen neuen Nutzer mit zufälliger E-Mail und liefert "Bearer <token>". */
	static String registerNewUser(MockMvc mockMvc) throws Exception {
		String json = """
				{ "email": "test-%s@example.com", "password": "geheim1234" }
				""".formatted(UUID.randomUUID());
		String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(response, "$.token");
	}

	static String athleteJson(int ftp, String eventName, LocalDate eventDate) {
		return """
				{
				  "ftp": %d,
				  "weightKg": 72.5,
				  "level": "AMBITIOUS",
				  "event": { "name": "%s", "date": "%s", "distanceKm": 227, "elevationMeters": 5500 },
				  "availability": { "monday": 0, "tuesday": 1.5, "wednesday": 0, "thursday": 1.5,
				                    "friday": 0, "saturday": 3, "sunday": 2 }
				}
				""".formatted(ftp, eventName, eventDate);
	}

	/** Registriert einen Nutzer, legt sein Profil an und liefert das Token. */
	static String registerWithProfile(MockMvc mockMvc) throws Exception {
		String token = registerNewUser(mockMvc);
		mockMvc.perform(put("/me/athlete").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(athleteJson(240, "Testevent", LocalDate.now().plusWeeks(10))));
		return token;
	}

}

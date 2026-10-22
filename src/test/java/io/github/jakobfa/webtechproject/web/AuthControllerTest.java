package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository userRepository;

	private static String credentials(String email, String password) {
		return "{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password);
	}

	@Test
	void registerReturnsTokenAndLoginWorksAfterwards() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("Lina@Example.com", "geheim1234")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.email").value("lina@example.com"));

		String response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("lina@example.com", "geheim1234")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + JsonPath.read(response, "$.token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("lina@example.com"));
	}

	@Test
	void storesOnlyAPasswordHash() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("hash@example.com", "geheim1234")));

		var user = userRepository.findByEmail("hash@example.com").orElseThrow();
		org.assertj.core.api.Assertions.assertThat(user.getPasswordHash())
				.doesNotContain("geheim1234")
				.startsWith("$2"); // BCrypt
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("doppelt@example.com", "geheim1234")));

		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("doppelt@example.com", "anderes1234")))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsShortPasswordAndInvalidEmail() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("kurz@example.com", "123")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("keine-email", "geheim1234")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsWrongPassword() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("falsch@example.com", "geheim1234")));

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("falsch@example.com", "falsch1234")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedRoutesNeedAToken() throws Exception {
		mockMvc.perform(get("/me/athlete")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me/plan")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me/athlete").header("Authorization", "Bearer gefälscht"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void workoutLibraryIsPublic() throws Exception {
		mockMvc.perform(get("/workouts")).andExpect(status().isOk());
	}


	@Test
	void registrationIsOpenWithoutInviteCode() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/registration"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inviteRequired")
						.value(false));
	}

}

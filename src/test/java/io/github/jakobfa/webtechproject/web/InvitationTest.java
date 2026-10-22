package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/** "Nur mit Einladung": jeder Code genau einmal */
@SpringBootTest(properties = "app.registration.invite-only=true")
@AutoConfigureMockMvc
class InvitationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private io.github.jakobfa.webtechproject.auth.InvitationRepository invitations;

	/** Ein vorhandenes Konto – direkt angelegt, weil die Registrierung hier einen Code braucht */
	private String existingUser() throws Exception {
		String email = "inviter-" + UUID.randomUUID() + "@example.com";
		users.save(new AppUser(email, passwordEncoder.encode("geheim1234"), Instant.now()));
		return "Bearer " + JsonPath.read(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"geheim1234\" }".formatted(email))).andReturn()
				.getResponse().getContentAsString(), "$.token");
	}

	private String newCode(String token) throws Exception {
		String body = mockMvc.perform(post("/me/invitations").header("Authorization", token))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.code");
	}

	private ResultActions register(String code) throws Exception {
		String codeJson = code == null ? "" : ", \"inviteCode\": \"%s\"".formatted(code);
		return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"guest-%s@example.com\", \"password\": \"geheim1234\"%s }"
						.formatted(UUID.randomUUID(), codeJson)));
	}

	@Test
	void eachCodeWorksExactlyOnce() throws Exception {
		String code = newCode(existingUser());
		assertThat(code).matches("QUAL-[A-Z2-9]{4}-[A-Z2-9]{4}");

		register(code).andExpect(status().isCreated());
		register(code).andExpect(status().isForbidden()); // zweites Konto mit demselben Code
	}

	@Test
	void withoutOrWithWrongCodeThereIsNoAccount() throws Exception {
		mockMvc.perform(get("/auth/registration")).andExpect(jsonPath("$.inviteRequired").value(true));
		register(null).andExpect(status().isForbidden());
		register("QUAL-AAAA-BBBB").andExpect(status().isForbidden());
	}

	@Test
	void typingIsForgiving() throws Exception {
		String code = newCode(existingUser());

		register(code.toLowerCase().replace("-", " ")).andExpect(status().isCreated());
	}

	@Test
	void atMostThreeOpenCodesAndUsedOnesShowUp() throws Exception {
		String token = existingUser();
		String first = newCode(token);
		newCode(token);
		newCode(token);
		mockMvc.perform(post("/me/invitations").header("Authorization", token)).andExpect(status().isConflict());

		register(first).andExpect(status().isCreated());
		newCode(token); // wieder Platz, weil einer benutzt ist

		mockMvc.perform(get("/me/invitations").header("Authorization", token))
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[?(@.status == 'USED')].lastChars").value(first.substring(first.length() - 4)))
				.andExpect(jsonPath("$[0].code").doesNotExist());
	}

	@Test
	void revokedCodeNoLongerWorksAndOthersCannotRevoke() throws Exception {
		String token = existingUser();
		String body = mockMvc.perform(post("/me/invitations").header("Authorization", token)).andReturn()
				.getResponse().getContentAsString();
		Number id = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");

		mockMvc.perform(delete("/me/invitations/" + id).header("Authorization", existingUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/me/invitations/" + id).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));
		register(code).andExpect(status().isForbidden());
	}

	@Test
	void theInvitationRemembersWhoUsedIt() throws Exception {
		String token = existingUser();
		String code = newCode(token);
		register(code);

		mockMvc.perform(get("/me/invitations").header("Authorization", token))
				.andExpect(jsonPath("$[0].status").value("USED"));
		org.assertj.core.api.Assertions.assertThat(invitations.findAll()).anySatisfy(invitation -> {
			org.assertj.core.api.Assertions.assertThat(invitation.getUsedBy()).isNotNull();
		});
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(post("/me/invitations")).andExpect(status().isUnauthorized());
	}

}

package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * "Nur mit Einladung": Der Betreiber erstellt Codes über die internen Routen, jeder Code gilt genau einmal.
 * Das Geheimnis ist in den Tests "test-sync-token" (siehe test/resources/application.properties).
 */
@SpringBootTest(properties = "app.registration.invite-only=true")
@AutoConfigureMockMvc
class InvitationTest {

	private static final String TOKEN = "test-sync-token";

	@Autowired
	private MockMvc mockMvc;

	private ResultActions createCode(String note) throws Exception {
		return mockMvc.perform(post("/internal/invitations").header("X-Sync-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"note\": \"%s\" }".formatted(note)));
	}

	private String newCode(String note) throws Exception {
		return JsonPath.read(createCode(note).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.code");
	}

	private ResultActions register(String email, String code) throws Exception {
		String codeJson = code == null ? "" : ", \"inviteCode\": \"%s\"".formatted(code);
		return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"geheim1234\"%s }".formatted(email, codeJson)));
	}

	private static String someEmail() {
		return "gast-" + UUID.randomUUID() + "@example.com";
	}

	@Test
	void eachCodeWorksExactlyOnce() throws Exception {
		String code = newCode("für Lina");
		assertThat(code).matches("QUAL-[A-Z2-9]{4}-[A-Z2-9]{4}");

		register(someEmail(), code).andExpect(status().isCreated());
		register(someEmail(), code).andExpect(status().isForbidden()); // zweites Konto mit demselben Code
	}

	@Test
	void withoutOrWithWrongCodeThereIsNoAccount() throws Exception {
		mockMvc.perform(get("/auth/registration")).andExpect(jsonPath("$.inviteRequired").value(true));
		register(someEmail(), null).andExpect(status().isForbidden());
		register(someEmail(), "QUAL-AAAA-BBBB").andExpect(status().isForbidden());
	}

	@Test
	void typingIsForgiving() throws Exception {
		String code = newCode("Tippfehler-Test");

		register(someEmail(), code.toLowerCase().replace("-", " ")).andExpect(status().isCreated());
	}

	@Test
	void listShowsNoteStatusAndWhoUsedIt() throws Exception {
		String email = someEmail();
		String code = newCode("für Tom");
		register(email, code);

		mockMvc.perform(get("/internal/invitations").header("X-Sync-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.note == 'für Tom')].status").value("USED"))
				.andExpect(jsonPath("$[?(@.note == 'für Tom')].usedBy").value(email))
				.andExpect(jsonPath("$[?(@.note == 'für Tom')].code").value((Object) null));
	}

	@Test
	void revokedCodeNoLongerWorks() throws Exception {
		String body = createCode("doch nicht").andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");

		mockMvc.perform(delete("/internal/invitations/" + id).header("X-Sync-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));
		register(someEmail(), code).andExpect(status().isForbidden());
	}

	@Test
	void withoutTheSecretTheRoutesDoNotExist() throws Exception {
		mockMvc.perform(post("/internal/invitations").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/internal/invitations").header("X-Sync-Token", "falsch"))
				.andExpect(status().isNotFound());
	}

	@Test
	void normalUsersCannotCreateCodes() throws Exception {
		mockMvc.perform(post("/me/invitations")).andExpect(status().isUnauthorized());
	}

}

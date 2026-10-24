package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.mail.MailService;

@SpringBootTest(properties = "app.mail.admin-notify=betreiber@example.com")
@AutoConfigureMockMvc
class FeedbackTest {

	private static final String ADMIN = "test-admin-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MailService mailService;

	private ResultActions send(String token, String json) throws Exception {
		return mockMvc.perform(post("/me/feedback").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void feedbackArrivesByMailAndInTheList() throws Exception {
		String token = registerNewUser(mockMvc);

		send(token, "{ \"message\": \"Der Sonntag <b>wirkt</b> komisch\", \"page\": \"/plan\" }")
				.andExpect(status().isCreated());

		verify(mailService).send(eq("betreiber@example.com"), contains("Feedback Nr."), contains("komisch"),
				contains("&lt;b&gt;wirkt&lt;/b&gt;"));
		String list = mockMvc.perform(get("/internal/feedback").header("X-Admin-Token", ADMIN))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(list, "$[0].id");
		mockMvc.perform(put("/internal/feedback/" + id + "/done").header("X-Admin-Token", ADMIN))
				.andExpect(jsonPath("$.done").value(true));
	}

	@Test
	void onlyLoggedInAndNotEmpty() throws Exception {
		mockMvc.perform(post("/me/feedback").contentType(MediaType.APPLICATION_JSON).content("{ \"message\": \"x\" }"))
				.andExpect(status().isUnauthorized());
		send(registerNewUser(mockMvc), "{ \"message\": \"   \" }").andExpect(status().isBadRequest());
		send(registerNewUser(mockMvc), "{ \"message\": \"%s\" }".formatted("a".repeat(2001)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void withoutTheAdminTokenTheListDoesNotExist() throws Exception {
		mockMvc.perform(get("/internal/feedback")).andExpect(status().isNotFound());
		mockMvc.perform(get("/internal/feedback").header("X-Admin-Token", "test-sync-token"))
				.andExpect(status().isNotFound());
	}

}

package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.monitoring.ErrorReporter;

@SpringBootTest(properties = "app.mail.admin-notify=betreiber@example.com")
@AutoConfigureMockMvc
class MonitoringTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ErrorReporter reporter;

	@MockitoBean
	private MailService mailService;

	@Test
	void healthIsPublicAndChecksTheDatabase() throws Exception {
		mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void errorsAreCollectedAndMailedOncePerHour() {
		reporter.sendDigest(); // leer: keine Mail
		verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());

		reporter.report("GET /me/plan", new IllegalStateException("kaputt"));
		reporter.report("GET /me/plan", new IllegalStateException("kaputt"));
		reporter.report("Nächtlicher Sync", new RuntimeException("Timeout"));
		reporter.sendDigest();

		verify(mailService).send(eq("betreiber@example.com"), contains("3 Fehler"),
				contains("2× GET /me/plan – IllegalStateException: kaputt"), contains("Nächtlicher Sync"));
		reporter.sendDigest(); // danach wieder leer
	}

	@Test
	void validationErrorsAreNotReported() throws Exception {
		mockMvc.perform(get("/planned-workouts/abc")).andExpect(status().is4xxClientError());
		assertThat(reporter).isNotNull();
	}

}

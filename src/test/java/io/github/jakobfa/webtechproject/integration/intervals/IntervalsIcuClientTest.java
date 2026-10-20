package io.github.jakobfa.webtechproject.integration.intervals;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Testet den Client gegen einen simulierten intervals.icu-Server – ohne echten Account und ohne Internet.
 */
class IntervalsIcuClientTest {

	private final IntervalsProperties properties = new IntervalsProperties("https://intervals.icu", "geheim", "0", 1L);

	private final IntervalsEvent event = new IntervalsEvent("WORKOUT", "2026-10-21T00:00:00", "Ride",
			"Sweet Spot 3 × 12", "Beschreibung", "sweet-spot-3x12.fit", "AAAA", "quaelmich-2026-10-21");

	@Test
	void sendsEventsWithBasicAuthToBulkEndpoint() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		IntervalsIcuClient client = new IntervalsIcuClient(properties, builder);

		server.expect(requestTo("https://intervals.icu/api/v1/athlete/0/events/bulk?upsert=true"))
				.andExpect(method(HttpMethod.POST))
				// "API_KEY:geheim" in Base64
				.andExpect(header("Authorization", "Basic QVBJX0tFWTpnZWhlaW0="))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].category").value("WORKOUT"))
				.andExpect(jsonPath("$[0].start_date_local").value("2026-10-21T00:00:00"))
				.andExpect(jsonPath("$[0].file_contents_base64").value("AAAA"))
				.andExpect(jsonPath("$[0].external_id").value("quaelmich-2026-10-21"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		client.upsertEvents(List.of(event));

		server.verify();
	}

	@Test
	void reportsErrorsFromIntervals() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		IntervalsIcuClient client = new IntervalsIcuClient(properties, builder);

		server.expect(requestTo("https://intervals.icu/api/v1/athlete/0/events/bulk?upsert=true"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.upsertEvents(List.of(event))).isInstanceOf(RestClientException.class);
	}

}

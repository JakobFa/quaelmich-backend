package io.github.jakobfa.webtechproject.integration.strava;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spricht mit der Strava-API: OAuth-Tokens tauschen und erneuern, Aktivitäten abholen.
 */
@Component
public class StravaClient {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TokenResponse(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("expires_at") long expiresAt,
			StravaAthlete athlete) {

		@Override
		public String toString() {
			return "TokenResponse[***]"; // Tokens nie ins Log
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StravaAthlete(Long id, String firstname) {
	}

	private final StravaProperties properties;
	private final RestClient restClient;

	@Autowired
	public StravaClient(StravaProperties properties) {
		this(properties, RestClient.builder());
	}

	/** Für Tests: erlaubt einen vorbereiteten Builder (z. B. mit MockRestServiceServer) */
	StravaClient(StravaProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	/** Tauscht den Code aus dem OAuth-Rücksprung gegen Tokens */
	public TokenResponse exchangeCode(String code) {
		return requestToken("authorization_code", "code", code);
	}

	public TokenResponse refresh(String refreshToken) {
		return requestToken("refresh_token", "refresh_token", refreshToken);
	}

	private TokenResponse requestToken(String grantType, String name, String value) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("grant_type", grantType);
		form.add(name, value);
		return restClient.post()
				.uri("/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);
	}

	/** Aktivitäten, die zwischen den beiden Zeitpunkten gestartet sind (höchstens 100) */
	public List<StravaActivity> listActivities(String accessToken, Instant after, Instant before) {
		List<StravaActivity> activities = restClient.get()
				.uri("/api/v3/athlete/activities?after={after}&before={before}&per_page=100", after.getEpochSecond(),
						before.getEpochSecond())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(new ParameterizedTypeReference<List<StravaActivity>>() {
				});
		return activities == null ? List.of() : activities;
	}

	/** Entzieht quaelmich den Zugriff auf das Strava-Konto */
	public void deauthorize(String accessToken) {
		restClient.post()
				.uri("/oauth/deauthorize")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.toBodilessEntity();
	}

}

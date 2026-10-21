package io.github.jakobfa.webtechproject.mail;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verschickt Mails über die HTTP-Schnittstelle von Resend (kein SMTP – das sperren Hoster oft).
 * Ohne API-Schlüssel (lokal, in Tests) wird die Mail nur ins Log geschrieben.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final MailProperties properties;
	private final RestClient restClient;

	@Autowired
	public MailService(MailProperties properties) {
		this(properties, RestClient.builder());
	}

	MailService(MailProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.apiBaseUrl()).build();
	}

	/** @return true, wenn die Mail an Resend übergeben wurde (oder lokal geloggt) */
	public boolean send(String to, String subject, String text, String html) {
		if (!properties.isConfigured()) {
			log.info("MAIL_API_KEY fehlt – Mail an {} nur geloggt:\n{}\n{}", to, subject, text);
			return true;
		}
		try {
			restClient.post()
					.uri("/emails")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("from", properties.from(), "to", List.of(to), "subject", subject, "text", text,
							"html", html))
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (RestClientException e) {
			log.warn("Mail an {} konnte nicht verschickt werden: {}", to, e.getMessage());
			return false;
		}
	}

	public String frontendUrl(String path) {
		return properties.frontendBaseUrl() + path;
	}

}

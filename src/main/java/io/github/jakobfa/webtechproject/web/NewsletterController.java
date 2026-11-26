package io.github.jakobfa.webtechproject.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rundmail an alle Nutzer – nur für den Betreiber, per Terminal (scripts/newsletter.sh).
 * Ohne oder mit falschem X-Admin-Token: 404. Ohne send=true wird nichts verschickt, nur die Empfängerliste gezeigt.
 * Verschickt wird jede Mail einzeln (niemand sieht die Adressen der anderen), und nur, wenn expectedCount zur Liste
 * passt – so geht nichts an eine Liste, die man vorher nicht gesehen hat. only: nur an diese Adressen (Testversand).
 * Antworten gehen an replyTo, ohne Angabe an dieselbe Adresse wie das Feedback (ADMIN_NOTIFY_EMAIL).
 */
@RestController
public class NewsletterController {

	private static final Logger log = LoggerFactory.getLogger(NewsletterController.class);

	/** Resend erlaubt im kostenlosen Tarif 2 Mails pro Sekunde */
	static long pauseMillis = 600;

	public record NewsletterRequest(@NotBlank @Size(max = 200) String subject, @NotBlank String text,
			@NotBlank String html, String replyTo, Boolean send, Integer expectedCount, List<String> only,
			List<String> exclude) {
	}

	public record NewsletterResponse(boolean sent, int count, String replyTo, List<String> recipients,
			List<String> failed) {
	}

	private final AppUserRepository users;
	private final MailService mail;
	private final AdminAccess access;
	private final String adminEmail;

	public NewsletterController(AppUserRepository users, MailService mail, AdminAccess access,
			@Value("${app.mail.admin-notify:}") String adminEmail) {
		this.users = users;
		this.mail = mail;
		this.access = access;
		this.adminEmail = adminEmail;
	}

	@PostMapping(path = "/internal/newsletter")
	public ResponseEntity<NewsletterResponse> newsletter(
			@RequestHeader(name = "X-Admin-Token", required = false) String token,
			@Valid @RequestBody NewsletterRequest request) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		List<String> recipients = recipients(request);
		String replyTo = request.replyTo() != null && !request.replyTo().isBlank() ? request.replyTo().trim()
				: adminEmail == null || adminEmail.isBlank() ? null : adminEmail.trim();
		if (!Boolean.TRUE.equals(request.send())) {
			return ResponseEntity.ok(new NewsletterResponse(false, recipients.size(), replyTo, recipients, List.of()));
		}
		if (request.expectedCount() == null || request.expectedCount() != recipients.size()) {
			// die Liste hat sich seit der Vorschau geändert (oder es gab keine) – lieber nichts verschicken
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new NewsletterResponse(false, recipients.size(), replyTo, recipients, List.of()));
		}
		List<String> failed = new ArrayList<>();
		for (int i = 0; i < recipients.size(); i++) {
			if (i > 0) {
				pause();
			}
			String to = recipients.get(i);
			if (!mail.send(to, request.subject(), request.text(), request.html(), replyTo)) {
				failed.add(to);
			}
		}
		log.info("Rundmail \"{}\": {} verschickt, {} fehlgeschlagen", request.subject(),
				recipients.size() - failed.size(), failed.size());
		return ResponseEntity.ok(new NewsletterResponse(true, recipients.size(), replyTo, recipients, failed));
	}

	private List<String> recipients(NewsletterRequest request) {
		Set<String> excluded = normalized(request.exclude());
		Set<String> only = normalized(request.only());
		return users.findAll().stream().map(AppUser::getEmail).filter(email -> email != null && !email.isBlank())
				.map(email -> email.trim())
				.filter(email -> !excluded.contains(email.toLowerCase(Locale.ROOT)))
				.filter(email -> only.isEmpty() || only.contains(email.toLowerCase(Locale.ROOT)))
				.distinct().sorted().toList();
	}

	private static Set<String> normalized(Collection<String> emails) {
		return emails == null ? Set.of()
				: emails.stream().filter(email -> email != null).map(email -> email.trim().toLowerCase(Locale.ROOT))
						.collect(Collectors.toSet());
	}

	private static void pause() {
		try {
			Thread.sleep(pauseMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}

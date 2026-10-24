package io.github.jakobfa.webtechproject.feedback;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/** Speichert Feedback und schickt dem Betreiber eine Mail (ADMIN_NOTIFY_EMAIL) */
@Service
public class FeedbackService {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withZone(ZoneId.of("Europe/Berlin"));

	private final FeedbackRepository repository;
	private final AppUserRepository users;
	private final MailService mailService;
	private final Clock clock;
	private final String adminEmail;

	public FeedbackService(FeedbackRepository repository, AppUserRepository users, MailService mailService,
			Clock clock, @Value("${app.mail.admin-notify:}") String adminEmail) {
		this.repository = repository;
		this.users = users;
		this.mailService = mailService;
		this.clock = clock;
		this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
	}

	@Transactional
	public Feedback submit(Long userId, String message, String page) {
		Feedback feedback = repository.save(new Feedback(users.getReferenceById(userId), message.strip(),
				page, Instant.now(clock)));
		notifyAdmin(users.findById(userId).map(user -> user.getEmail()).orElse("?"), feedback);
		return feedback;
	}

	public List<Feedback> listAll() {
		return repository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional
	public Optional<Feedback> markDone(Long id) {
		return repository.findById(id).map(feedback -> {
			feedback.setDoneAt(Instant.now(clock));
			return feedback;
		});
	}

	private void notifyAdmin(String email, Feedback feedback) {
		if (adminEmail.isEmpty()) {
			return;
		}
		String when = TIME.format(feedback.getCreatedAt());
		String text = "Neues Feedback bei quaelmich (Nr. %d)\nvon %s am %s Uhr, Seite %s\n\n%s\n"
				.formatted(feedback.getId(), email, when, feedback.getPage(), feedback.getMessage());
		String html = "<p>Neues Feedback bei quaelmich (Nr. %d)<br>von <strong>%s</strong> am %s Uhr, Seite %s</p>"
				.formatted(feedback.getId(), escape(email), when, escape(String.valueOf(feedback.getPage())))
				+ "<blockquote style=\"white-space:pre-wrap\">" + escape(feedback.getMessage()) + "</blockquote>";
		Runnable send = () -> mailService.send(adminEmail, "Feedback Nr. " + feedback.getId(), text, html);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					send.run();
				}
			});
		} else {
			send.run();
		}
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}

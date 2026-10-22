package io.github.jakobfa.webtechproject.auth;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.model.AppUser;

/**
 * Schickt dem Betreiber eine kurze Mail, wenn sich jemand neu registriert (ADMIN_NOTIFY_EMAIL).
 * Erst nach dem Speichern des Kontos – schlägt die Registrierung fehl, gibt es keine Mail.
 */
@Component
public class RegistrationNotifier {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withZone(ZoneId.of("Europe/Berlin"));

	private final MailService mailService;
	private final String adminEmail;

	public RegistrationNotifier(MailService mailService, @Value("${app.mail.admin-notify:}") String adminEmail) {
		this.mailService = mailService;
		this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
	}

	/** @param invitation welcher Einladungscode benutzt wurde (mit Notiz), null ohne Code */
	public void newRegistration(AppUser user, String invitation) {
		if (adminEmail.isEmpty()) {
			return;
		}
		String when = TIME.format(user.getCreatedAt());
		String via = invitation == null ? "ohne Einladungscode" : "mit " + invitation;
		String text = "Neues Konto bei quaelmich:\n\n%s\nam %s Uhr, %s\n".formatted(user.getEmail(), when, via);
		String html = "<p>Neues Konto bei quaelmich:</p><p><strong>%s</strong><br>am %s Uhr, %s</p>"
				.formatted(escape(user.getEmail()), when, escape(via));
		Runnable send = () -> mailService.send(adminEmail, "Neue Registrierung: " + user.getEmail(), text, html);
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

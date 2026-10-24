package io.github.jakobfa.webtechproject.monitoring;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.mail.MailService;

/**
 * Sammelt unerwartete Fehler und schickt dem Betreiber höchstens einmal pro Stunde eine Mail
 * (ADMIN_NOTIFY_EMAIL). Nur technische Angaben – keine Nutzerdaten: Ort, Fehlerart, gekürzte Meldung, Anzahl.
 */
@Component
public class ErrorReporter {

	/** gleiche Fehler zusammenfassen; nie mehr als so viele verschiedene pro Mail */
	static final int MAX_DISTINCT = 20;
	static final int MAX_MESSAGE = 200;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
			.withZone(ZoneId.of("Europe/Berlin"));

	record Entry(String where, String type, String message, int count, Instant first, Instant last) {
	}

	private final MailService mailService;
	private final String adminEmail;
	private final Clock clock;
	private final Map<String, Entry> pending = new LinkedHashMap<>();

	public ErrorReporter(MailService mailService, @Value("${app.mail.admin-notify:}") String adminEmail,
			Clock clock) {
		this.mailService = mailService;
		this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
		this.clock = clock;
	}

	/** where = z. B. "GET /me/plan" oder "Nächtlicher Sync" */
	public synchronized void report(String where, Throwable error) {
		String type = error.getClass().getSimpleName();
		String message = shorten(error.getMessage());
		String key = where + "|" + type;
		Instant now = Instant.now(clock);
		Entry existing = pending.get(key);
		if (existing != null) {
			pending.put(key, new Entry(where, type, existing.message(), existing.count() + 1, existing.first(), now));
		} else if (pending.size() < MAX_DISTINCT) {
			pending.put(key, new Entry(where, type, message, 1, now, now));
		}
	}

	/** jede volle Stunde: gesammelte Fehler verschicken */
	@Scheduled(cron = "0 0 * * * *")
	public void sendDigest() {
		Map<String, Entry> batch;
		synchronized (this) {
			if (pending.isEmpty()) {
				return;
			}
			batch = new LinkedHashMap<>(pending);
			pending.clear();
		}
		if (adminEmail.isEmpty()) {
			return;
		}
		int total = batch.values().stream().mapToInt(Entry::count).sum();
		StringBuilder text = new StringBuilder("quaelmich hatte in der letzten Stunde %d unerwartete Fehler:\n\n"
				.formatted(total));
		StringBuilder html = new StringBuilder("<p>quaelmich hatte in der letzten Stunde <strong>%d</strong> unerwartete Fehler:</p><ul>"
				.formatted(total));
		for (Entry entry : batch.values()) {
			String line = "%d× %s – %s: %s (%s–%s)".formatted(entry.count(), entry.where(), entry.type(),
					entry.message(), TIME.format(entry.first()), TIME.format(entry.last()));
			text.append("• ").append(line).append('\n');
			html.append("<li>").append(escape(line)).append("</li>");
		}
		html.append("</ul><p>Details stehen im Render-Log zur selben Zeit.</p>");
		text.append("\nDetails stehen im Render-Log zur selben Zeit.\n");
		mailService.send(adminEmail, "quaelmich: %d Fehler in der letzten Stunde".formatted(total), text.toString(),
				html.toString());
	}

	synchronized int pendingCount() {
		return pending.values().stream().mapToInt(Entry::count).sum();
	}

	private static String shorten(String message) {
		if (message == null) {
			return "(ohne Meldung)";
		}
		String oneLine = message.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= MAX_MESSAGE ? oneLine : oneLine.substring(0, MAX_MESSAGE) + "…";
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}

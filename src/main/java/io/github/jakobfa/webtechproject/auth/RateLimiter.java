package io.github.jakobfa.webtechproject.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Einfache Bremse gegen Bots: höchstens n Versuche pro Zeitfenster und Schlüssel (z. B. "login:1.2.3.4").
 * Liegt nur im Arbeitsspeicher – nach einem Neustart fängt die Zählung neu an, für quaelmich reicht das.
 */
@Component
public class RateLimiter {

	/** Erlaubte Versuche pro Zeitfenster */
	public record Limit(int attempts, Duration window) {
	}

	public static final Limit REGISTER = new Limit(5, Duration.ofHours(1));
	public static final Limit LOGIN = new Limit(10, Duration.ofMinutes(15));
	public static final Limit RESET_REQUEST_PER_IP = new Limit(5, Duration.ofHours(1));
	public static final Limit RESET_REQUEST_PER_EMAIL = new Limit(3, Duration.ofHours(1));
	public static final Limit RESET_CONFIRM = new Limit(10, Duration.ofHours(1));

	private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
	private final Clock clock;
	private final boolean enabled;

	public RateLimiter(Clock clock, @Value("${app.rate-limit.enabled:true}") boolean enabled) {
		this.clock = clock;
		this.enabled = enabled;
	}

	/** Zählt den Versuch mit; false, wenn das Limit schon erreicht ist (dann wird nicht mitgezählt) */
	public boolean tryAcquire(String key, Limit limit) {
		if (!enabled) {
			return true;
		}
		Instant now = clock.instant();
		Deque<Instant> recent = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (recent) {
			while (!recent.isEmpty() && !recent.peekFirst().isAfter(now.minus(limit.window()))) {
				recent.pollFirst();
			}
			if (recent.size() >= limit.attempts()) {
				return false;
			}
			recent.addLast(now);
			return true;
		}
	}

	/**
	 * IP-Adresse des Aufrufers. Hinter dem Proxy von Render steht sie in X-Forwarded-For; der letzte Eintrag
	 * ist der, den Render selbst gesehen hat (frühere Einträge könnte der Aufrufer fälschen).
	 */
	public static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			String[] parts = forwarded.split(",");
			return parts[parts.length - 1].trim();
		}
		return request.getRemoteAddr();
	}

	/** Für Tests */
	void reset() {
		attempts.clear();
	}

}

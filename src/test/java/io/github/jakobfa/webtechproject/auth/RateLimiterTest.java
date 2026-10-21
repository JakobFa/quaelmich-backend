package io.github.jakobfa.webtechproject.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

	/** Uhr, die man von Hand weiterstellen kann */
	private static class MovableClock extends Clock {
		Instant now = Instant.parse("2026-10-21T10:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	private final MovableClock clock = new MovableClock();
	private final RateLimiter limiter = new RateLimiter(clock, true);
	private final RateLimiter.Limit threePerHour = new RateLimiter.Limit(3, Duration.ofHours(1));

	@Test
	void blocksAfterTheLimitAndAllowsAgainLater() {
		assertThat(limiter.tryAcquire("login:1.2.3.4", threePerHour)).isTrue();
		assertThat(limiter.tryAcquire("login:1.2.3.4", threePerHour)).isTrue();
		assertThat(limiter.tryAcquire("login:1.2.3.4", threePerHour)).isTrue();
		assertThat(limiter.tryAcquire("login:1.2.3.4", threePerHour)).isFalse();
		assertThat(limiter.tryAcquire("login:5.6.7.8", threePerHour)).as("andere Adresse").isTrue();

		clock.now = clock.now.plus(Duration.ofMinutes(61));
		assertThat(limiter.tryAcquire("login:1.2.3.4", threePerHour)).isTrue();
	}

	@Test
	void canBeSwitchedOff() {
		RateLimiter off = new RateLimiter(clock, false);
		for (int i = 0; i < 10; i++) {
			assertThat(off.tryAcquire("x", threePerHour)).isTrue();
		}
	}

	@Test
	void takesTheAddressRenderSaw() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1");
		assertThat(RateLimiter.clientIp(request)).isEqualTo("10.0.0.1");

		// vom Aufrufer gefälschter erster Eintrag, Render hängt die echte Adresse hinten an
		request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");
		assertThat(RateLimiter.clientIp(request)).isEqualTo("203.0.113.7");
	}

}

package io.github.jakobfa.webtechproject.mcp;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthCodeRepository extends JpaRepository<OAuthCode, String> {

	void deleteByExpiresAtBefore(Instant time);
}

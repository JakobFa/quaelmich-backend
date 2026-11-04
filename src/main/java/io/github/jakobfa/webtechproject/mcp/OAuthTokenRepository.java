package io.github.jakobfa.webtechproject.mcp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthTokenRepository extends JpaRepository<OAuthToken, Long> {

	Optional<OAuthToken> findByAccessHash(String accessHash);

	Optional<OAuthToken> findByRefreshHash(String refreshHash);

	List<OAuthToken> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(Long userId);

	void deleteByUserId(Long userId);
}

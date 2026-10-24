package io.github.jakobfa.webtechproject.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	/** Mit Sperre, damit derselbe Code nicht gleichzeitig zweimal eingelöst wird */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Invitation> findByCodeHash(String codeHash);

	List<Invitation> findAllByOrderByCreatedAtDesc();


	List<Invitation> findByUsedById(Long userId);

}

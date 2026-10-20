package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Verwaltet die intervals.icu-Verbindungen der Nutzer: verbinden (mit Prüfung), trennen,
 * und die entschlüsselten Zugangsdaten für einen Sync bereitstellen.
 */
@Service
public class IntervalsConnectionService {

	/** Wird geworfen, wenn intervals.icu die Zugangsdaten ablehnt. */
	public static class InvalidCredentialsException extends RuntimeException {
	}

	private final IntervalsConnectionRepository repository;
	private final AppUserRepository userRepository;
	private final IntervalsIcuClient client;
	private final SecretCipher cipher;
	private final Clock clock;

	public IntervalsConnectionService(IntervalsConnectionRepository repository, AppUserRepository userRepository,
			IntervalsIcuClient client, SecretCipher cipher, Clock clock) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.client = client;
		this.cipher = cipher;
		this.clock = clock;
	}

	public Optional<IntervalsConnection> findByOwner(Long userId) {
		return repository.findByOwnerId(userId);
	}

	/** Prüft die Zugangsdaten bei intervals.icu und speichert sie nur, wenn sie funktionieren. */
	@Transactional
	public IntervalsConnection connect(Long userId, String apiKey, String athleteId) {
		String normalizedId = athleteId == null || athleteId.isBlank() ? "0" : athleteId.trim();
		IntervalsCredentials credentials = new IntervalsCredentials(apiKey.trim(), normalizedId);
		if (!client.verify(credentials)) {
			throw new InvalidCredentialsException();
		}

		IntervalsConnection connection = repository.findByOwnerId(userId).orElseGet(() -> {
			IntervalsConnection created = new IntervalsConnection();
			created.setOwner(userRepository.getReferenceById(userId));
			return created;
		});
		connection.setEncryptedApiKey(cipher.encrypt(credentials.apiKey()));
		connection.setIntervalsAthleteId(normalizedId);
		connection.setConnectedAt(clock.instant());
		return repository.save(connection);
	}

	@Transactional
	public void disconnect(Long userId) {
		repository.findByOwnerId(userId).ifPresent(repository::delete);
	}

	public Optional<IntervalsCredentials> credentialsOf(Long userId) {
		return repository.findByOwnerId(userId).map(connection -> new IntervalsCredentials(
				cipher.decrypt(connection.getEncryptedApiKey()), connection.getIntervalsAthleteId()));
	}

}

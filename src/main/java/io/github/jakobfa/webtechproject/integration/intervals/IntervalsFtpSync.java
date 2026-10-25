package io.github.jakobfa.webtechproject.integration.intervals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Neue FTP im Profil → auch in intervals.icu eintragen. Die Workouts gehen dort in % FTP hin; ohne die neue
 * FTP würden intervals.icu (und der Radcomputer, der von dort lädt) mit dem alten Wert rechnen.
 */
@Service
public class IntervalsFtpSync {

	private static final Logger log = LoggerFactory.getLogger(IntervalsFtpSync.class);

	private final IntervalsConnectionService connections;
	private final IntervalsIcuClient client;

	public IntervalsFtpSync(IntervalsConnectionService connections, IntervalsIcuClient client) {
		this.connections = connections;
		this.client = client;
	}

	/** Nur mit verbundenem intervals.icu; ein Fehler dort verhindert das Speichern in quaelmich nicht */
	public void pushFtp(Long userId, int ftp) {
		connections.credentialsOf(userId).ifPresent(credentials -> {
			try {
				client.updateFtp(credentials, ftp);
			} catch (RestClientException e) {
				log.warn("FTP für Nutzer {} nicht an intervals.icu übertragen: {}", userId, e.getMessage());
			}
		});
	}

}

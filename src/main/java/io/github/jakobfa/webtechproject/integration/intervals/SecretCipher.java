package io.github.jakobfa.webtechproject.integration.intervals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Verschlüsselt Geheimnisse (hier: die intervals.icu-API-Schlüssel der Nutzer) mit AES-256-GCM,
 * bevor sie in die Datenbank geschrieben werden.
 *
 * Format: Base64( 12 Byte Zufalls-IV | Chiffretext inkl. 16 Byte Prüfsumme ).
 * Wegen der Prüfsumme fällt jede Manipulation am gespeicherten Wert beim Entschlüsseln auf.
 */
@Component
public class SecretCipher {

	private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public SecretCipher(IntervalsProperties properties) {
		this(properties.encryptionKey());
	}

	SecretCipher(String base64Key) {
		byte[] keyBytes;
		if (base64Key == null || base64Key.isBlank()) {
			log.warn("INTERVALS_ENCRYPTION_KEY ist nicht gesetzt – es wird ein zufälliger Schlüssel verwendet. "
					+ "Gespeicherte intervals.icu-Verbindungen sind dann nach einem Neustart unbrauchbar.");
			keyBytes = new byte[32];
			random.nextBytes(keyBytes);
		} else {
			keyBytes = Base64.getDecoder().decode(base64Key.trim());
			if (keyBytes.length != 32) {
				throw new IllegalStateException("INTERVALS_ENCRYPTION_KEY muss 32 Byte (Base64-kodiert) lang sein");
			}
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
					.put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Verschlüsseln fehlgeschlagen", e);
		}
	}

	public String decrypt(String encoded) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Entschlüsseln fehlgeschlagen", e);
		}
	}

}

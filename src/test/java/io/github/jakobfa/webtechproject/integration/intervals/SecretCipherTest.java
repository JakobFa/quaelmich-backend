package io.github.jakobfa.webtechproject.integration.intervals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private final SecretCipher cipher = new SecretCipher(KEY);

	@Test
	void decryptsWhatItEncrypted() {
		String encrypted = cipher.encrypt("mein-geheimer-schluessel");

		assertThat(encrypted).doesNotContain("geheim");
		assertThat(cipher.decrypt(encrypted)).isEqualTo("mein-geheimer-schluessel");
	}

	@Test
	void sameTextGivesDifferentCiphertextEachTime() {
		// zufälliger IV pro Verschlüsselung → gleiche Schlüssel sind in der DB nicht erkennbar
		assertThat(cipher.encrypt("abc")).isNotEqualTo(cipher.encrypt("abc"));
	}

	@Test
	void detectsTamperedValues() {
		String encrypted = cipher.encrypt("abc");
		byte[] bytes = Base64.getDecoder().decode(encrypted);
		bytes[bytes.length - 1] ^= 1;

		assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anotherKeyCannotDecrypt() {
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;
		SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString(otherKey));

		assertThatThrownBy(() -> other.decrypt(cipher.encrypt("abc"))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsKeysOfWrongLength() {
		assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
				.isInstanceOf(IllegalStateException.class);
	}

}

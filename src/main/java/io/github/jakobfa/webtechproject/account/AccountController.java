package io.github.jakobfa.webtechproject.account;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;

@RestController
public class AccountController {

	private final AccountService accounts;

	public AccountController(AccountService accounts) {
		this.accounts = accounts;
	}

	public record DeleteRequest(String password) {
	}

	/** Konto endgültig löschen – mit Passwort bestätigt. 204, 403 bei falschem Passwort. */
	@PostMapping(path = "/me/account/delete")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @RequestBody DeleteRequest request) {
		try {
			accounts.delete(CurrentUser.id(jwt), request.password());
			return ResponseEntity.noContent().build();
		} catch (AccountService.WrongPasswordException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
	}

	/** Alle Daten als JSON-Datei */
	@GetMapping(path = "/me/account/export")
	public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal Jwt jwt) {
		return accounts.export(CurrentUser.id(jwt))
				.map(data -> ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quaelmich-daten.json\"")
						.body(data))
				.orElse(ResponseEntity.notFound().build());
	}

}

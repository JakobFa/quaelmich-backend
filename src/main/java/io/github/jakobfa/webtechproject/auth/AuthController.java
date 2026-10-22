package io.github.jakobfa.webtechproject.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.AuthService.EmailAlreadyRegisteredException;
import io.github.jakobfa.webtechproject.auth.AuthService.InvalidInvitationException;
import io.github.jakobfa.webtechproject.auth.PasswordResetService.InvalidTokenException;
import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class AuthController {

	/** inviteCode nur nötig, wenn auf dem Server ein Einladungscode gesetzt ist */
	public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password,
			@Size(max = 100) String inviteCode) {
	}

	public record RegistrationInfo(boolean inviteRequired) {
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record AuthResponse(String token, String email) {
	}

	public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
	}

	public record PasswordResetConfirmation(@NotBlank @Size(max = 100) String token,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record MeResponse(Long id, String email) {
	}

	private final AuthService authService;
	private final TokenService tokenService;
	private final PasswordResetService passwordResetService;
	private final RateLimiter rateLimiter;
	private final InvitationService invitations;

	public AuthController(AuthService authService, TokenService tokenService,
			PasswordResetService passwordResetService, RateLimiter rateLimiter, InvitationService invitations) {
		this.authService = authService;
		this.tokenService = tokenService;
		this.passwordResetService = passwordResetService;
		this.rateLimiter = rateLimiter;
		this.invitations = invitations;
	}

	/** Braucht die Registrierung gerade einen Einladungscode? (für das Formular) */
	@GetMapping(path = "/auth/registration")
	public RegistrationInfo registrationInfo() {
		return new RegistrationInfo(invitations.isInviteOnly());
	}

	/** 403 = Einladungscode fehlt, stimmt nicht oder ist schon benutzt; 429 = zu viele Versuche */
	@PostMapping(path = "/auth/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest http) {
		if (!rateLimiter.tryAcquire("register:" + RateLimiter.clientIp(http), RateLimiter.REGISTER)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		try {
			AppUser user = authService.register(request.email(), request.password(), request.inviteCode());
			return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
		} catch (EmailAlreadyRegisteredException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (InvalidInvitationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
	}

	@PostMapping(path = "/auth/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		if (!rateLimiter.tryAcquire("login:" + RateLimiter.clientIp(http), RateLimiter.LOGIN)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		return authService.authenticate(request.email(), request.password())
				.map(user -> ResponseEntity.ok(toResponse(user)))
				.orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	/**
	 * "Passwort vergessen": antwortet immer mit 202, egal ob es das Konto gibt – sonst ließe sich herausfinden,
	 * welche Adressen registriert sind. Begrenzt pro Adresse und pro E-Mail, damit niemand Fremde zuspammt.
	 */
	@PostMapping(path = "/auth/password-reset")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
			HttpServletRequest http) {
		String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (!rateLimiter.tryAcquire("reset-ip:" + RateLimiter.clientIp(http), RateLimiter.RESET_REQUEST_PER_IP)
				|| !rateLimiter.tryAcquire("reset-mail:" + email, RateLimiter.RESET_REQUEST_PER_EMAIL)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		passwordResetService.requestReset(email);
		return ResponseEntity.accepted().build();
	}

	/** Neues Passwort mit dem Link aus der Mail setzen. 400, wenn der Link ungültig, abgelaufen oder benutzt ist. */
	@PostMapping(path = "/auth/password-reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation request,
			HttpServletRequest http) {
		if (!rateLimiter.tryAcquire("reset-confirm:" + RateLimiter.clientIp(http), RateLimiter.RESET_CONFIRM)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		try {
			passwordResetService.resetPassword(request.token(), request.password());
			return ResponseEntity.noContent().build();
		} catch (InvalidTokenException e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@GetMapping(path = "/auth/me")
	public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return authService.findById(CurrentUser.id(jwt))
				.map(user -> ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail())))
				.orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	private AuthResponse toResponse(AppUser user) {
		return new AuthResponse(tokenService.issue(user), user.getEmail());
	}

}

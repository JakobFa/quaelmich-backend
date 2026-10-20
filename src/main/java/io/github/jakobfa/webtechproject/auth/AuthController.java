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
import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class AuthController {

	public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record AuthResponse(String token, String email) {
	}

	public record MeResponse(Long id, String email) {
	}

	private final AuthService authService;
	private final TokenService tokenService;

	public AuthController(AuthService authService, TokenService tokenService) {
		this.authService = authService;
		this.tokenService = tokenService;
	}

	@PostMapping(path = "/auth/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		try {
			AppUser user = authService.register(request.email(), request.password());
			return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
		} catch (EmailAlreadyRegisteredException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@PostMapping(path = "/auth/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return authService.authenticate(request.email(), request.password())
				.map(user -> ResponseEntity.ok(toResponse(user)))
				.orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
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

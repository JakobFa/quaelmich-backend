package io.github.jakobfa.webtechproject.config;

import java.util.Set;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.service.AiDecisionTracker;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ändert der Nutzer in der App etwas an seinem Plan, ist es seine Entscheidung: geänderte Einheiten verlieren das
 * KI-Zeichen (siehe {@link AiDecisionTracker}). Gilt für ändernde Anfragen mit Login (nicht für die KI über /mcp).
 * Markiert wird, bevor die Antwort geschrieben wird – damit sie schon den neuen Stand zeigt; Antworten ohne Inhalt
 * erledigt afterCompletion.
 */
@Component
@ControllerAdvice
public class UserDecisionInterceptor implements HandlerInterceptor, ResponseBodyAdvice<Object> {

	private static final String SNAPSHOT = UserDecisionInterceptor.class.getName() + ".snapshot";

	private final AiDecisionTracker tracker;

	public UserDecisionInterceptor(AiDecisionTracker tracker) {
		this.tracker = tracker;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Long userId = userId();
		if (userId != null && isChange(request)) {
			request.setAttribute(SNAPSHOT, tracker.snapshot(userId));
		}
		return true;
	}

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
			Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converterType,
			ServerHttpRequest request, ServerHttpResponse response) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return body;
		}
		int status = response instanceof ServletServerHttpResponse servletResponse
				? servletResponse.getServletResponse().getStatus() : 200;
		Set<Long> changed = mark(servletRequest.getServletRequest(), status);
		if (body instanceof PlannedWorkoutResponse planned && changed.contains(planned.id())) {
			return new PlannedWorkoutResponse(planned.id(), planned.date(), planned.phase(), planned.completed(),
					planned.rating(), planned.skipReason(), planned.workout(), planned.actual(), planned.adjustment(),
					planned.options(), false);
		}
		return body;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		if (ex == null) {
			mark(request, response.getStatus());
		}
	}

	/** Einmal je Anfrage: der Snapshot wird dabei verbraucht */
	private Set<Long> mark(HttpServletRequest request, int status) {
		Object snapshot = request.getAttribute(SNAPSHOT);
		request.removeAttribute(SNAPSHOT);
		Long userId = userId();
		if (snapshot instanceof AiDecisionTracker.Snapshot before && userId != null && status < 400) {
			return tracker.markChanges(userId, before, false);
		}
		return Set.of();
	}

	private static boolean isChange(HttpServletRequest request) {
		String method = request.getMethod();
		return !"GET".equals(method) && !"OPTIONS".equals(method) && !"HEAD".equals(method);
	}

	private static Long userId() {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
			try {
				return CurrentUser.id(token.getToken());
			} catch (RuntimeException e) {
				return null;
			}
		}
		return null;
	}

}

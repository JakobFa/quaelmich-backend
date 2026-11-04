package io.github.jakobfa.webtechproject.mcp;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Schaltet die KI-Anbindung (MCP) ab, solange app.mcp.enabled nicht true ist: Der MCP-Dienst, die Anmeldung für
 * KI-Apps (OAuth) und die Verwaltung im Profil antworten dann mit 404, als gäbe es sie nicht.
 * <p>
 * Abgeschaltet, weil der Plan aus Strava-Werten abgeleitet sein kann und die Strava API Policy (§5.3) verbietet,
 * Abgeleitetes in eine KI zu laden. Läuft vor allen anderen Filtern (auch vor Spring Security, das
 * /.well-known/oauth-protected-resource selbst ausliefert).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpSwitchFilter extends OncePerRequestFilter {

	private final boolean enabled;

	public McpSwitchFilter(@Value("${app.mcp.enabled:false}") boolean enabled) {
		this.enabled = enabled;
	}

	static boolean isMcpPath(String path) {
		return path.equals("/mcp") || path.startsWith("/mcp/") || path.startsWith("/oauth/")
				|| path.startsWith("/.well-known/oauth-") || path.equals("/me/ai-connections")
				|| path.startsWith("/me/ai-connections/");
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return enabled || !isMcpPath(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}
}

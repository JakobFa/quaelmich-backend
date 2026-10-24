package io.github.jakobfa.webtechproject.monitoring;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Wird als letzter gefragt – also nur bei Fehlern, die sonst niemand behandelt (die später zu einer 500
 * werden). Meldet sie dem ErrorReporter und lässt Spring dann normal weitermachen.
 */
@Component
public class ErrorReportingResolver implements HandlerExceptionResolver, Ordered {

	private final ErrorReporter reporter;

	public ErrorReportingResolver(ErrorReporter reporter) {
		this.reporter = reporter;
	}

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		// Zahlen in Pfaden (IDs) zusammenfassen, damit gleiche Fehler gleich gezählt werden
		String path = request.getRequestURI().replaceAll("/\\d+", "/{id}");
		reporter.report(request.getMethod() + " " + path, ex);
		return null;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}

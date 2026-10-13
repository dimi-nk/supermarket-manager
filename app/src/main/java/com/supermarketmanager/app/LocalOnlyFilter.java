package com.supermarketmanager.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The server binds to 127.0.0.1, but web pages in the user's browser can still reach it. Two checks close that:
 * <ul>
 *   <li>Host must be a loopback name, which defeats DNS rebinding.</li>
 *   <li>Cross-site requests are refused: an {@code Origin} header must be a loopback origin, and
 *       {@code Sec-Fetch-Site: cross-site} is rejected. This stops other sites from sending "simple" POSTs
 *       (no CORS preflight) to endpoints such as shutdown or apply-run. Non-browser clients send neither header.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalOnlyFilter extends OncePerRequestFilter {
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (!LOOPBACK.contains(hostName(req.getHeader("Host")))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Only local requests are accepted");
            return;
        }
        String origin = req.getHeader("Origin");
        if (origin != null && !"null".equals(origin) && !loopbackOrigin(origin)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin requests are not accepted");
            return;
        }
        if ("null".equals(origin) || "cross-site".equalsIgnoreCase(req.getHeader("Sec-Fetch-Site"))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site requests are not accepted");
            return;
        }
        chain.doFilter(req, res);
    }

    static String hostName(String host) {
        if (host == null) return "";
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) return h.contains("]") ? h.substring(0, h.indexOf(']') + 1) : h;
        int colon = h.indexOf(':');
        return colon >= 0 ? h.substring(0, colon) : h;
    }

    static boolean loopbackOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost();
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && host != null && LOOPBACK.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

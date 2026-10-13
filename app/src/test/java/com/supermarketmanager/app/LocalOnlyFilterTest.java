package com.supermarketmanager.app;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOnlyFilterTest {

    @ParameterizedTest(name = "Host={0} Origin={1} Sec-Fetch-Site={2} -> {3}")
    @CsvSource(nullValues = "-", value = {
            "localhost:8765, -, -, 200",
            "127.0.0.1:8765, -, -, 200",
            "[::1]:8765, -, -, 200",
            "LOCALHOST, -, -, 200",
            "evil.example:8765, -, -, 403",          // DNS rebinding
            "-, -, -, 403",
            "localhost:8765, http://localhost:5173, same-site, 200", // Vite dev server
            "localhost:8765, http://127.0.0.1:8765, same-origin, 200",
            "localhost:8765, https://evil.example, cross-site, 403", // drive-by POST from another site
            "localhost:8765, http://localhost.evil.example, -, 403",
            "localhost:8765, null, -, 403",           // sandboxed iframe / file://
            "localhost:8765, -, cross-site, 403",
            "localhost:8765, not a url, -, 403",
    })
    void acceptsOnlyLocalSameSiteRequests(String host, String origin, String fetchSite, int expected) throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/system/shutdown");
        if (host != null) req.addHeader("Host", host);
        if (origin != null) req.addHeader("Origin", origin);
        if (fetchSite != null) req.addHeader("Sec-Fetch-Site", fetchSite);
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new LocalOnlyFilter().doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(expected);
        assertThat(chain.getRequest() != null).isEqualTo(expected == 200);
    }
}

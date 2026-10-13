package com.supermarketmanager.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * Entry point. Modes:
 * <ul>
 *   <li>default (desktop): local server on 127.0.0.1, opens the UI in the browser, tray icon with Open/Quit.</li>
 *   <li>{@code --server}: local server only (no browser, no tray).</li>
 *   <li>{@code --mcp-stdio}: MCP over stdin/stdout for agents that spawn processes; no web server, logs to file.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.supermarketmanager")
public class SupermarketManagerApplication {

    public static void main(String[] args) {
        boolean stdio = Arrays.asList(args).contains("--mcp-stdio");
        boolean server = Arrays.asList(args).contains("--server");
        DataDir.configure();

        var app = new SpringApplication(SupermarketManagerApplication.class);
        if (stdio) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles("stdio");
        } else if (!server) {
            app.setHeadless(false);
            if (DesktopLauncher.focusRunningInstance()) return;
        }
        ConfigurableApplicationContext ctx = app.run(args);
        if (!stdio && !server) DesktopLauncher.start(ctx);
    }
}

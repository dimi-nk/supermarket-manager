package com.supermarketmanager.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final ConfigurableApplicationContext ctx;
    private final String version;
    private final String dataDir;
    private final int port;

    public SystemController(ConfigurableApplicationContext ctx, @Value("${sm.version:dev}") String version,
                            @Value("${sm.data-dir}") String dataDir, @Value("${server.port:8765}") int port) {
        this.ctx = ctx;
        this.version = version;
        this.dataDir = dataDir;
        this.port = port;
    }

    public record Info(String app, String version, String dataDir, String database, String mcpUrl, String java) {
    }

    @GetMapping("/info")
    public Info info() {
        return new Info("supermarket-manager", version, dataDir, Path.of(dataDir, "supermarket-manager.db").toString(),
                "http://localhost:" + port + "/mcp", System.getProperty("java.version"));
    }

    /** Stops the app (for platforms without a tray icon). */
    @PostMapping("/shutdown")
    public Map<String, String> shutdown() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(ctx, () -> 0));
        });
        t.setDaemon(false);
        t.start();
        return Map.of("message", "Shutting down");
    }
}

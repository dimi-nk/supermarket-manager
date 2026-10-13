package com.supermarketmanager.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Desktop niceties: single instance, open the browser, tray icon. Everything degrades gracefully when unavailable. */
final class DesktopLauncher {
    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    private DesktopLauncher() {
    }

    static int port() {
        String p = System.getenv("SM_PORT");
        return p == null || p.isBlank() ? 8765 : Integer.parseInt(p.trim());
    }

    static String url() {
        return "http://localhost:" + port() + "/";
    }

    /** If an instance already answers on our port, open its UI and report true (the caller then exits). */
    static boolean focusRunningInstance() {
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
            var res = client.send(HttpRequest.newBuilder(URI.create(url() + "api/system/info")).timeout(Duration.ofSeconds(1)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body().contains("supermarket-manager")) {
                openBrowser();
                return true;
            }
        } catch (Exception ignored) {
            // nothing running
        }
        return false;
    }

    static void start(ConfigurableApplicationContext ctx) {
        openBrowser();
        installTray(ctx);
    }

    static void openBrowser() {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url()));
                return;
            }
        } catch (Exception e) {
            log.debug("Desktop browse failed", e);
        }
        log.info("Open {} in your browser", url());
    }

    private static void installTray(ConfigurableApplicationContext ctx) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return;
            var menu = new PopupMenu();
            var open = new MenuItem("Open Supermarket Manager");
            open.addActionListener(e -> openBrowser());
            var quit = new MenuItem("Quit");
            quit.addActionListener(e -> {
                SystemTray.getSystemTray().getTrayIcons();
                for (TrayIcon t : SystemTray.getSystemTray().getTrayIcons()) SystemTray.getSystemTray().remove(t);
                System.exit(SpringApplication.exit(ctx, () -> 0));
            });
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);
            var icon = new TrayIcon(icon(), "Supermarket Manager", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openBrowser());
            SystemTray.getSystemTray().add(icon);
        } catch (Exception e) {
            log.info("No system tray available; use the Quit button in the web UI to stop the app");
        }
    }

    /** A small basket glyph drawn in code, so no image asset is needed at runtime. */
    private static Image icon() {
        var img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x2f7d4f));
        g.fillRoundRect(3, 12, 26, 16, 6, 6);
        g.setStroke(new java.awt.BasicStroke(3f));
        g.drawArc(8, 3, 16, 18, 0, 180);
        g.dispose();
        return img;
    }
}

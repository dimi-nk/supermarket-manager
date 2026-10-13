package com.supermarketmanager.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Where the SQLite database and logs live: SM_DATA_DIR, else the OS-conventional per-user app-data folder. */
final class DataDir {
    private DataDir() {
    }

    static Path resolve() {
        String override = System.getenv("SM_DATA_DIR");
        if (override == null || override.isBlank()) override = System.getProperty("sm.data-dir");
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("mac")) return home.resolve("Library/Application Support/SupermarketManager");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return (appData != null ? Path.of(appData) : home.resolve("AppData/Roaming")).resolve("SupermarketManager");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".local/share")).resolve("supermarket-manager");
    }

    static void configure() {
        Path dir = resolve();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create data directory " + dir, e);
        }
        System.setProperty("sm.data-dir", dir.toAbsolutePath().toString());
    }
}

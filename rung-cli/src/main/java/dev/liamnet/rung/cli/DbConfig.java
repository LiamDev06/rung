package dev.liamnet.rung.cli;

import dev.liamnet.rung.SimpleDataSource;
import java.util.Map;

public final class DbConfig {

    private DbConfig() {}

    public static SimpleDataSource resolve(
            final String url, final String user, final String password, final Map<String, String> environment) {
        final String resolvedUrl = firstNonBlank(url, environment.get("DATABASE_URL"));
        if (resolvedUrl == null) {
            throw new IllegalStateException("No database URL: pass --url <jdbc-url> or set DATABASE_URL.");
        }

        return new SimpleDataSource(
                resolvedUrl,
                firstNonBlank(user, environment.get("DATABASE_USER")),
                firstNonBlank(password, environment.get("DATABASE_PASSWORD")));
    }

    private static String firstNonBlank(final String preferred, final String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }

        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        return null;
    }
}

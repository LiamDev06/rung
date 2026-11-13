package dev.liamnet.rung.internal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationLayout {

    private static final String PLACEHOLDER = "{module}";

    private final Pattern pathPattern;

    public MigrationLayout(final String layout) {
        final String normalized = layout.strip().replaceAll("^/+|/+$", "");
        final int placeholder = normalized.indexOf(PLACEHOLDER);
        if (placeholder < 0 || placeholder != normalized.lastIndexOf(PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Layout must contain " + PLACEHOLDER + " exactly once, but was: " + layout);
        }

        final String before = normalized.substring(0, placeholder);
        final String after = normalized.substring(placeholder + PLACEHOLDER.length());
        this.pathPattern = Pattern.compile(Pattern.quote(before) + "([^/]+)" + Pattern.quote(after) + "/([^/]+)");
    }

    public Optional<Location> locate(final String relativePath) {
        final Matcher matcher = pathPattern.matcher(relativePath);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(new Location(matcher.group(1), matcher.group(2)));
    }

    public record Location(String module, String script) {}
}

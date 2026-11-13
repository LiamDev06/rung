package dev.liamnet.rung.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MigrationFile(String version, String script, String upSql, String downSql) {

    private static final Pattern NAME = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern UP_MARKER = Pattern.compile("^\\s*--\\s*\\+migrate\\s+up\\s*$");
    private static final Pattern DOWN_MARKER = Pattern.compile("^\\s*--\\s*\\+migrate\\s+down\\s*$");

    public static MigrationFile parse(final String script, final String content) {
        final Matcher name = NAME.matcher(script);
        if (!name.matches()) {
            throw new IllegalArgumentException(
                    "Migration file must be named V<version>__<description>.sql, but was: " + script);
        }

        final String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        final StringBuilder upBlock = new StringBuilder();
        final StringBuilder downBlock = new StringBuilder();
        StringBuilder current = null;
        boolean sawUp = false;

        for (final String line : normalized.split("\n", -1)) {
            if (UP_MARKER.matcher(line).matches()) {
                current = upBlock;
                sawUp = true;
            } else if (DOWN_MARKER.matcher(line).matches()) {
                current = downBlock;
            } else if (current != null) {
                current.append(line).append('\n');
            }
        }

        if (!sawUp) {
            throw new IllegalArgumentException("Migration " + script + " has no '-- +migrate up' section.");
        }

        return new MigrationFile(
                name.group(1),
                script,
                upBlock.toString().strip(),
                downBlock.toString().strip());
    }

    public BigInteger number() {
        return new BigInteger(version);
    }

    public boolean hasDown() {
        return !downSql.isBlank();
    }

    public String checksum() {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(upSql.getBytes(UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

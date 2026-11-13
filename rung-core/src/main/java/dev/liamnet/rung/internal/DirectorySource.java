package dev.liamnet.rung.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class DirectorySource implements MigrationSource {

    private final Path directory;

    public DirectorySource(final Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public Map<String, String> loadSqlFiles() {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Migration directory does not exist: " + directory);
        }

        try {
            return readSqlFiles();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read migrations under " + directory, e);
        }
    }

    private Map<String, String> readSqlFiles() throws IOException {
        final List<Path> sqlFiles;
        try (Stream<Path> paths = Files.walk(directory)) {
            sqlFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList();
        }

        final Map<String, String> files = new TreeMap<>();
        for (final Path file : sqlFiles) {
            final String relativePath = directory.relativize(file).toString().replace(File.separatorChar, '/');
            files.put(relativePath, Files.readString(file, UTF_8));
        }
        return files;
    }
}

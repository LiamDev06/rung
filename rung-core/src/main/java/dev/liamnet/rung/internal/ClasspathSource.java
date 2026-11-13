package dev.liamnet.rung.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ClasspathSource implements MigrationSource {

    private static final String FILE_PROTOCOL = "file";

    private final String basePath;
    private final ClassLoader classLoader;

    public ClasspathSource(final String basePath) {
        this(basePath, defaultClassLoader());
    }

    public ClasspathSource(final String basePath, final ClassLoader classLoader) {
        this.basePath = basePath.replaceAll("^/+|/+$", "");
        this.classLoader = classLoader;
    }

    @Override
    public Map<String, String> loadSqlFiles() {
        final Map<String, String> files = new TreeMap<>();
        try {
            for (final URL root : Collections.list(classLoader.getResources(basePath))) {
                collect(root, files);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to scan migration resources under classpath:" + basePath, e);
        }
        return files;
    }

    private static void collect(final URL root, final Map<String, String> files) throws IOException {
        if (FILE_PROTOCOL.equals(root.getProtocol())) {
            new DirectorySource(toPath(root)).loadSqlFiles().forEach(files::putIfAbsent);
            return;
        }

        final URLConnection connection = root.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)) {
            throw new IllegalStateException("Unsupported classpath location: " + root);
        }

        collectFromJar(jarConnection, files);
    }

    private static void collectFromJar(final JarURLConnection connection, final Map<String, String> files)
            throws IOException {
        connection.setUseCaches(false);

        try (JarFile jar = connection.getJarFile()) {
            final String entryName = connection.getEntryName();
            final String prefix = entryName == null || entryName.endsWith("/") ? entryName : entryName + "/";

            for (final JarEntry entry : Collections.list(jar.entries())) {
                final String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".sql") || prefix != null && !name.startsWith(prefix)) {
                    continue;
                }

                final String relativePath = prefix == null ? name : name.substring(prefix.length());
                if (!files.containsKey(relativePath)) {
                    files.put(relativePath, read(jar, entry));
                }
            }
        }
    }

    private static String read(final JarFile jar, final JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), UTF_8);
        }
    }

    private static Path toPath(final URL url) {
        try {
            return Path.of(url.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException("Unsupported classpath location: " + url, e);
        }
    }

    private static ClassLoader defaultClassLoader() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
    }
}

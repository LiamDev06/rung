package dev.liamnet.rung.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathSourceTests {

    @Test
    void readsSqlFilesFromTheTestClasspathDirectory() {
        final ClasspathSource source = new ClasspathSource("dev/liamnet/rung/testfixtures/paired");

        assertThat(source.loadSqlFiles())
                .containsKeys(
                        "widget/migrations/V001__create_widget.sql",
                        "widget/migrations/V002__add_widget_name.sql",
                        "widget/migrations/_V003__draft.sql");
    }

    @Test
    void readsSqlFilesPackagedInsideAJar(@TempDir final Path dir) throws IOException {
        final Path jar = dir.resolve("app.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream output = new JarOutputStream(file)) {
            addEntry(output, "com/", null);
            addEntry(output, "com/acme/", null);
            addEntry(output, "com/acme/Widget.class", "not sql");
            addEntry(output, "com/acme/widget/migrations/V001__create_widget.sql", "-- +migrate up\nSELECT 1;\n");
            addEntry(output, "com/other/widget/migrations/V001__elsewhere.sql", "-- +migrate up\nSELECT 2;\n");
        }

        try (URLClassLoader classLoader =
                new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            final ClasspathSource source = new ClasspathSource("com/acme", classLoader);

            assertThat(source.loadSqlFiles())
                    .containsOnlyKeys("widget/migrations/V001__create_widget.sql")
                    .containsValue("-- +migrate up\nSELECT 1;\n");
        }
    }

    @Test
    void returnsNothingWhenTheBasePathIsNotOnTheClasspath() {
        assertThat(new ClasspathSource("no/such/package").loadSqlFiles()).isEmpty();
    }

    private static void addEntry(final JarOutputStream output, final String name, final String content)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        if (content != null) {
            output.write(content.getBytes(UTF_8));
        }
        output.closeEntry();
    }
}

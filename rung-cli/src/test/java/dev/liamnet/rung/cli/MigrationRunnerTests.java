package dev.liamnet.rung.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

class MigrationRunnerTests {

    private static final String PAIRED =
            "-- +migrate up\nCREATE TABLE widget (id INT);\n-- +migrate down\nDROP TABLE widget;\n";
    private static final String UP_ONLY = "-- +migrate up\nCREATE TABLE widget (id INT);\n";

    private final CommandLine cli = new CommandLine(new MigrationRunner());
    private final StringWriter output = new StringWriter();

    @Test
    void migrateBindsTheDirectoryAndDefaults() {
        final ParseResult result = cli.parseArgs("migrate", "--dir", "db");

        final MigrationRunner.Migrate migrate = subcommand(result);
        assertThat(migrate.source.dir).isEqualTo(Path.of("db"));
        assertThat(migrate.source.layout).isEqualTo("{module}/migrations");
        assertThat(migrate.database.tablePrefix).isEqualTo("rung_migrations_");
    }

    @Test
    void migrateBindsLayoutAndTablePrefix() {
        final ParseResult result =
                cli.parseArgs("migrate", "--dir", "db", "--layout", "{module}", "--table-prefix", "schema_");

        final MigrationRunner.Migrate migrate = subcommand(result);
        assertThat(migrate.source.layout).isEqualTo("{module}");
        assertThat(migrate.database.tablePrefix).isEqualTo("schema_");
    }

    @Test
    void rollbackBindsModuleAndSteps() {
        final ParseResult result = cli.parseArgs("rollback", "user", "2", "--dir", "db", "--url", "jdbc:x");

        final MigrationRunner.Rollback rollback = subcommand(result);
        assertThat(rollback.module).isEqualTo("user");
        assertThat(rollback.steps).isEqualTo(2);
        assertThat(rollback.database.url).isEqualTo("jdbc:x");
    }

    @Test
    void rollbackStepsDefaultsToOne() {
        final ParseResult result = cli.parseArgs("rollback", "user", "--dir", "db");

        final MigrationRunner.Rollback rollback = subcommand(result);
        assertThat(rollback.steps).isEqualTo(1);
    }

    @Test
    void missingDirectoryIsAUsageError() {
        assertThat(execute("migrate")).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void rollbackWithoutModuleIsAUsageError() {
        assertThat(execute("rollback", "--dir", "db")).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void unknownCommandIsAUsageError() {
        assertThat(execute("bogus", "--dir", "db")).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void validatePassesForWellFormedMigrations(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/V001__create_widget.sql", PAIRED);

        assertThat(execute("validate", "--dir", dir.toString())).isZero();
        assertThat(output.toString()).contains("Validation passed: 1 module(s), 0 problem(s).");
    }

    @Test
    void validateFailsAndNamesTheMisnamedFile(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/create_widget.sql", PAIRED);

        assertThat(execute("validate", "--dir", dir.toString())).isEqualTo(1);
        assertThat(output.toString()).contains("error [widget]").contains("create_widget.sql");
    }

    @Test
    void validateOnlyFailsOnAMissingDownSectionWhenStrict(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/V001__create_widget.sql", UP_ONLY);

        assertThat(execute("validate", "--dir", dir.toString())).isZero();
        assertThat(execute("validate", "--dir", dir.toString(), "--strict")).isEqualTo(1);
        assertThat(output.toString()).contains("warning [widget]");
    }

    private static void write(final Path root, final String relativePath, final String content) throws IOException {
        final Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static <T> T subcommand(final ParseResult result) {
        return result.subcommand().commandSpec().commandLine().getCommand();
    }

    private int execute(final String... args) {
        return new CommandLine(new MigrationRunner())
                .setErr(new PrintWriter(output))
                .setOut(new PrintWriter(output))
                .execute(args);
    }
}

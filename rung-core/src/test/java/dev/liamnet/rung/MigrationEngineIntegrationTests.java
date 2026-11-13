package dev.liamnet.rung;

import static dev.liamnet.rung.TestDatabases.columnExists;
import static dev.liamnet.rung.TestDatabases.execute;
import static dev.liamnet.rung.TestDatabases.h2DataSource;
import static dev.liamnet.rung.TestDatabases.rowCount;
import static dev.liamnet.rung.TestDatabases.tableExists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationEngineIntegrationTests {

    private static final String PAIRED = "dev.liamnet.rung.testfixtures.paired";
    private static final String BROKEN = "dev.liamnet.rung.testfixtures.broken";

    @Test
    void migrateAppliesPendingMigrationsInVersionOrder() {
        final SimpleDataSource dataSource = h2DataSource("engine_forward");

        MigrationEngine.forClasspath(PAIRED).migrate(dataSource);

        assertThat(tableExists(dataSource, "widget")).isTrue();
        assertThat(columnExists(dataSource, "widget", "name")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);
    }

    @Test
    void migrateIsIdempotentWhenRerunWithNothingPending() {
        final SimpleDataSource dataSource = h2DataSource("engine_idempotent");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);

        engine.migrate(dataSource);
        engine.migrate(dataSource);

        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);
    }

    @Test
    void migrateRejectsAMigrationEditedAfterItWasApplied() {
        final SimpleDataSource dataSource = h2DataSource("engine_checksum");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);
        engine.migrate(dataSource);

        execute(dataSource, "UPDATE rung_migrations_widget SET checksum = 'tampered' WHERE version = '001'");

        assertThatThrownBy(() -> engine.migrate(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modified after it was applied");
    }

    @Test
    void failedMigrationHaltsAndLeavesNoHistoryRow() {
        final SimpleDataSource dataSource = h2DataSource("engine_failure");

        assertThatThrownBy(() -> MigrationEngine.forClasspath(BROKEN).migrate(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Migration failed for module 'thing'");

        assertThat(tableExists(dataSource, "thing")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_thing")).isEqualTo(1);
    }

    @Test
    void migrateReadsMigrationsFromAFilesystemDirectory(@TempDir final Path dir) throws Exception {
        final Path migrations = Files.createDirectories(dir.resolve("widget/migrations"));
        Files.writeString(
                migrations.resolve("V001__create_widget.sql"),
                "-- +migrate Up\nCREATE TABLE widget (id INT PRIMARY KEY);\n"
                        + "-- +migrate Down\nDROP TABLE widget;\n");
        final SimpleDataSource dataSource = h2DataSource("engine_directory");

        MigrationEngine.forDirectory(dir).migrate(dataSource);

        assertThat(tableExists(dataSource, "widget")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(1);
    }
}

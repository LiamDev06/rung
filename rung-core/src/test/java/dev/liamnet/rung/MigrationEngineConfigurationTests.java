package dev.liamnet.rung;

import static dev.liamnet.rung.TestDatabases.h2DataSource;
import static dev.liamnet.rung.TestDatabases.rowCount;
import static dev.liamnet.rung.TestDatabases.tableExists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationEngineConfigurationTests {

    private static final String PAIRED = "dev.liamnet.rung.testfixtures.paired";
    private static final String CREATE_WIDGET =
            "-- +migrate Up\nCREATE TABLE widget (id INT PRIMARY KEY);\n-- +migrate Down\nDROP TABLE widget;\n";

    @Test
    void customTablePrefixNamesTheHistoryTable() {
        final SimpleDataSource dataSource = h2DataSource("config_prefix");
        final MigrationEngine engine = MigrationEngine.builder()
                .classpath(PAIRED)
                .tablePrefix("schema_history_")
                .build();

        engine.migrate(dataSource);

        assertThat(rowCount(dataSource, "schema_history_widget")).isEqualTo(2);
        assertThat(tableExists(dataSource, "rung_migrations_widget")).isFalse();

        engine.rollback(dataSource, "widget", 2);

        assertThat(rowCount(dataSource, "schema_history_widget")).isZero();
    }

    @Test
    void rejectsATablePrefixThatIsNotASafeIdentifier() {
        assertThatThrownBy(() -> MigrationEngine.builder()
                        .classpath(PAIRED)
                        .tablePrefix("history; DROP TABLE users")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table prefix");
    }

    @Test
    void customLayoutReadsMigrationsFromACentralFolder(@TempDir final Path dir) throws IOException {
        write(dir, "widget/V001__create_widget.sql", CREATE_WIDGET);
        write(dir, "widget/migrations/V002__ignored_by_this_layout.sql", "-- +migrate Up\nSELECT broken;\n");
        final SimpleDataSource dataSource = h2DataSource("config_layout");
        final MigrationEngine engine =
                MigrationEngine.builder().directory(dir).layout("{module}").build();

        engine.migrate(dataSource);

        assertThat(engine.discoverModules()).containsExactly("widget");
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(1);
    }

    @Test
    void locationStringSelectsClasspathOrFilesystem(@TempDir final Path dir) throws IOException {
        write(dir, "gizmo/migrations/V001__create_gizmo.sql", CREATE_WIDGET);

        assertThat(MigrationEngine.builder()
                        .location("filesystem:" + dir)
                        .build()
                        .discoverModules())
                .containsExactly("gizmo");
        assertThat(MigrationEngine.builder()
                        .location("classpath:dev/liamnet/rung/testfixtures/paired")
                        .build()
                        .discoverModules())
                .containsExactly("widget");
        assertThat(MigrationEngine.builder().location(PAIRED).build().discoverModules())
                .containsExactly("widget");
    }

    @Test
    void missingDirectoryFailsInsteadOfReportingNothingToDo(@TempDir final Path dir) {
        final MigrationEngine engine = MigrationEngine.forDirectory(dir.resolve("typo"));

        assertThatThrownBy(engine::discoverModules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void migrateRejectsTwoMigrationsWithTheSameVersion(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/V1__create_widget.sql", CREATE_WIDGET);
        write(dir, "widget/migrations/V001__create_widget_again.sql", CREATE_WIDGET);
        final SimpleDataSource dataSource = h2DataSource("config_duplicate");

        assertThatThrownBy(() -> MigrationEngine.forDirectory(dir).migrate(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("two migrations with version 1");

        assertThat(tableExists(dataSource, "widget")).isFalse();
    }

    @Test
    void timestampVersionsAreOrderedNumerically(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/V20261113120000__create_widget.sql", CREATE_WIDGET);
        write(
                dir,
                "widget/migrations/V20261114090000__add_name.sql",
                "-- +migrate Up\nALTER TABLE widget ADD COLUMN name varchar(255);\n");
        final SimpleDataSource dataSource = h2DataSource("config_timestamps");

        MigrationEngine.forDirectory(dir).migrate(dataSource);

        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);
    }

    @Test
    void validateIsCleanForWellFormedMigrations() {
        assertThat(MigrationEngine.forClasspath(PAIRED).validate()).isEmpty();
    }

    @Test
    void validateReportsEveryProblemWithoutADatabase(@TempDir final Path dir) throws IOException {
        write(dir, "widget/migrations/create_widget.sql", CREATE_WIDGET);
        write(dir, "widget/migrations/V002__no_up.sql", "CREATE TABLE widget (id INT);\n");
        write(dir, "widget/migrations/V003__first.sql", CREATE_WIDGET);
        write(dir, "widget/migrations/V3__second.sql", CREATE_WIDGET);
        write(dir, "widget/migrations/V004__no_down.sql", "-- +migrate Up\nSELECT 1;\n");
        write(dir, "widget/migrations/_V005__draft.sql", "anything goes");
        write(dir, "Bad-Name/migrations/V001__create.sql", CREATE_WIDGET);

        final List<MigrationProblem> problems =
                MigrationEngine.forDirectory(dir).validate();

        assertThat(problems)
                .extracting(MigrationProblem::describe)
                .containsExactlyInAnyOrder(
                        "error [Bad-Name] Module name must match [a-z0-9_]+.",
                        "error [widget] Migration file must be named V<version>__<description>.sql,"
                                + " but was: create_widget.sql",
                        "error [widget] Migration V002__no_up.sql has no '-- +migrate Up' section.",
                        "error [widget] Version 3 is used by both V003__first.sql and V3__second.sql.",
                        "warning [widget] Migration V004__no_down.sql has no '-- +migrate Down' section.");
    }

    private static void write(final Path root, final String relativePath, final String content) throws IOException {
        final Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

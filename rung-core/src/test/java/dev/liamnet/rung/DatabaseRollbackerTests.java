package dev.liamnet.rung;

import static dev.liamnet.rung.TestDatabases.columnExists;
import static dev.liamnet.rung.TestDatabases.h2DataSource;
import static dev.liamnet.rung.TestDatabases.rowCount;
import static dev.liamnet.rung.TestDatabases.tableExists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseRollbackerTests {

    private static final String PAIRED = "dev.liamnet.rung.testfixtures.paired";
    private static final String UNPAIRED = "dev.liamnet.rung.testfixtures.unpaired";

    @Test
    void rollbackRemovesLatestMigrationAndItsHistoryRow() {
        final SimpleDataSource dataSource = h2DataSource("rollback_latest");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);
        engine.migrate(dataSource);

        assertThat(columnExists(dataSource, "widget", "name")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);

        engine.rollback(dataSource, "widget", 1);

        assertThat(columnExists(dataSource, "widget", "name")).isFalse();
        assertThat(tableExists(dataSource, "widget")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(1);
    }

    @Test
    void multiStepRollbackUnwindsEveryMigration() {
        final SimpleDataSource dataSource = h2DataSource("rollback_multi");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);
        engine.migrate(dataSource);

        engine.rollback(dataSource, "widget", 2);

        assertThat(tableExists(dataSource, "widget")).isFalse();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isZero();
    }

    @Test
    void rollbackStopsGracefullyWhenNothingLeft() {
        final SimpleDataSource dataSource = h2DataSource("rollback_overrun");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);
        engine.migrate(dataSource);

        assertThatCode(() -> engine.rollback(dataSource, "widget", 5)).doesNotThrowAnyException();

        assertThat(tableExists(dataSource, "widget")).isFalse();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isZero();
    }

    @Test
    void migrateAfterRollbackRestoresSchema() {
        final SimpleDataSource dataSource = h2DataSource("rollback_then_remigrate");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);

        engine.migrate(dataSource);
        engine.rollback(dataSource, "widget", 1);
        assertThat(columnExists(dataSource, "widget", "name")).isFalse();

        engine.migrate(dataSource);

        assertThat(columnExists(dataSource, "widget", "name")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);
    }

    @Test
    void migrationWithoutADownSectionFailsAndLeavesTheDatabaseUntouched() {
        final SimpleDataSource dataSource = h2DataSource("rollback_missing_down");
        final MigrationEngine engine = MigrationEngine.forClasspath(UNPAIRED);
        engine.migrate(dataSource);

        assertThatThrownBy(() -> engine.rollback(dataSource, "gadget", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be rolled back");

        assertThat(tableExists(dataSource, "gadget")).isTrue();
        assertThat(rowCount(dataSource, "rung_migrations_gadget")).isEqualTo(1);
    }

    @Test
    void draftMigrationsPrefixedWithUnderscoreAreIgnored() {
        final SimpleDataSource dataSource = h2DataSource("draft_ignored");
        MigrationEngine.forClasspath(PAIRED).migrate(dataSource);

        assertThat(tableExists(dataSource, "widget_draft")).isFalse();
        assertThat(rowCount(dataSource, "rung_migrations_widget")).isEqualTo(2);
    }

    @Test
    void rollbackOfANeverMigratedModuleDoesNothing() {
        final SimpleDataSource dataSource = h2DataSource("rollback_never_migrated");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);

        assertThatCode(() -> engine.rollback(dataSource, "widget", 1)).doesNotThrowAnyException();

        assertThat(tableExists(dataSource, "rung_migrations_widget")).isFalse();
    }

    @Test
    void rejectsInvalidModuleNameBeforeTouchingTheDatabase() {
        final SimpleDataSource dataSource = h2DataSource("rollback_invalid_module");
        final MigrationEngine engine = MigrationEngine.forClasspath(PAIRED);

        assertThatThrownBy(() -> engine.rollback(dataSource, "widget; DROP TABLE users", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid module name");
    }
}

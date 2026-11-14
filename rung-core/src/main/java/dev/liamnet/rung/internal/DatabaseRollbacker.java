package dev.liamnet.rung.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatabaseRollbacker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseRollbacker.class);

    private final DataSource dataSource;
    private final SchemaHistory history;
    private final String module;
    private final Map<String, String> scripts;
    private final int steps;

    public DatabaseRollbacker(
            final DataSource dataSource,
            final SchemaHistory history,
            final String module,
            final Map<String, String> scripts,
            final int steps) {
        this.dataSource = dataSource;
        this.history = history;
        this.module = module;
        this.scripts = scripts;
        this.steps = steps;
    }

    public void run() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            try (MigrationLock ignored = MigrationLock.acquire(connection, history.tableName())) {
                rollbackSteps(connection);
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Rollback failed for module '" + module + "'", e);
        }
    }

    private void rollbackSteps(final Connection connection) throws SQLException {
        if (!history.exists(connection)) {
            LOGGER.info("Nothing to roll back in module '{}'.", module);
            return;
        }

        for (int step = 0; step < steps; step++) {
            if (!rollbackOne(connection)) {
                LOGGER.info("Nothing left to roll back in module '{}'.", module);
                return;
            }
        }
    }

    private boolean rollbackOne(final Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        boolean committed = false;

        try {
            final AppliedMigration applied = history.latest(connection);
            if (applied == null) {
                return false;
            }

            execute(connection, downScript(applied));
            history.delete(connection, applied.version());
            connection.commit();
            committed = true;
            LOGGER.info("Rolled back {} (module '{}').", applied.script(), module);
            return true;
        } finally {
            if (!committed) {
                connection.rollback();
            }
            connection.setAutoCommit(true);
        }
    }

    private String downScript(final AppliedMigration applied) {
        final MigrationFile migration = load(applied.script());
        if (!migration.hasDown()) {
            throw new IllegalStateException("Migration " + applied.script() + " in module '" + module
                    + "' has no '-- +migrate down' section, so it cannot be rolled back.");
        }

        return migration.downSql();
    }

    private MigrationFile load(final String script) {
        final String content = scripts.get(script);
        if (content == null) {
            throw new IllegalStateException(
                    "Migration file " + script + " for module '" + module + "' is missing; cannot roll back.");
        }

        return MigrationFile.parse(script, content);
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

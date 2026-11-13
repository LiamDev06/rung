package dev.liamnet.rung.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class MigrationLock implements AutoCloseable {

    private final Connection connection;
    private final long key;
    private final boolean held;

    private MigrationLock(final Connection connection, final long key, final boolean held) {
        this.connection = connection;
        this.key = key;
        this.held = held;
    }

    public static MigrationLock acquire(final Connection connection, final String lockName) throws SQLException {
        final long key = lockName.hashCode();
        if (!isPostgres(connection)) {
            return new MigrationLock(connection, key, false);
        }

        execute(connection, "SELECT pg_advisory_lock(" + key + ")");
        return new MigrationLock(connection, key, true);
    }

    @Override
    public void close() throws SQLException {
        if (held) {
            execute(connection, "SELECT pg_advisory_unlock(" + key + ")");
        }
    }

    private static boolean isPostgres(final Connection connection) throws SQLException {
        return connection
                .getMetaData()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT)
                .contains("postgresql");
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

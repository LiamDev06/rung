package dev.liamnet.rung;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class TestDatabases {

    private TestDatabases() {}

    static SimpleDataSource h2DataSource(final String name) {
        return new SimpleDataSource(
                "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static boolean tableExists(final SimpleDataSource dataSource, final String table) {
        return queryable(dataSource, "SELECT 1 FROM " + table + " WHERE 1 = 0");
    }

    static boolean columnExists(final SimpleDataSource dataSource, final String table, final String column) {
        return queryable(dataSource, "SELECT " + column + " FROM " + table + " WHERE 1 = 0");
    }

    static int rowCount(final SimpleDataSource dataSource, final String table) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (final SQLException e) {
            throw new IllegalStateException("Failed to count history rows in " + table, e);
        }
    }

    static void execute(final SimpleDataSource dataSource, final String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (final SQLException e) {
            throw new IllegalStateException("Failed to execute: " + sql, e);
        }
    }

    private static boolean queryable(final SimpleDataSource dataSource, final String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (final SQLException e) {
            return false;
        }
    }
}

package dev.liamnet.rung;

import dev.liamnet.rung.internal.ClasspathSource;
import dev.liamnet.rung.internal.DatabaseRollbacker;
import dev.liamnet.rung.internal.DirectorySource;
import dev.liamnet.rung.internal.MigrationFile;
import dev.liamnet.rung.internal.MigrationLayout;
import dev.liamnet.rung.internal.MigrationLock;
import dev.liamnet.rung.internal.MigrationSource;
import dev.liamnet.rung.internal.SchemaHistory;
import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MigrationEngine {

    public static final String DEFAULT_LAYOUT = "{module}/migrations";
    public static final String DEFAULT_TABLE_PREFIX = "rung_migrations_";

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEngine.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILESYSTEM_PREFIX = "filesystem:";
    private static final String DRAFT_PREFIX = "_";

    private final MigrationSource source;
    private final MigrationLayout layout;
    private final String tablePrefix;

    private MigrationEngine(final MigrationSource source, final MigrationLayout layout, final String tablePrefix) {
        this.source = source;
        this.layout = layout;
        this.tablePrefix = tablePrefix;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MigrationEngine forClasspath(final String basePackage) {
        return builder().classpath(basePackage).build();
    }

    public static MigrationEngine forDirectory(final Path directory) {
        return builder().directory(directory).build();
    }

    public void migrate(final DataSource dataSource) {
        final Map<String, List<MigrationFile>> modules = loadModules();
        int applied = 0;
        for (final Map.Entry<String, List<MigrationFile>> module : modules.entrySet()) {
            applied += migrateModule(dataSource, module.getKey(), module.getValue());
        }

        if (applied == 0) {
            LOGGER.info("No pending migrations; all modules are already up to date.");
        } else {
            LOGGER.info("Applied {} migration(s).", applied);
        }
    }

    public void rollback(final DataSource dataSource, final String module, final int steps) {
        final SchemaHistory history = new SchemaHistory(tablePrefix, module);
        final Map<String, String> scripts = scriptsByModule().getOrDefault(module, Map.of());
        new DatabaseRollbacker(dataSource, history, module, scripts, steps).run();
    }

    public void status(final DataSource dataSource) {
        loadModules().forEach((module, migrations) -> statusModule(dataSource, module, migrations));
    }

    public List<MigrationProblem> validate() {
        final List<MigrationProblem> problems = new ArrayList<>();
        scriptsByModule().forEach((module, scripts) -> validateModule(module, scripts, problems));
        return problems;
    }

    public Set<String> discoverModules() {
        return scriptsByModule().keySet();
    }

    private Map<String, Map<String, String>> scriptsByModule() {
        final Map<String, Map<String, String>> modules = new TreeMap<>();
        source.loadSqlFiles()
                .forEach((path, content) -> layout.locate(path)
                        .filter(location -> !location.script().startsWith(DRAFT_PREFIX))
                        .ifPresent(location -> modules.computeIfAbsent(location.module(), module -> new TreeMap<>())
                                .put(location.script(), content)));
        return modules;
    }

    private Map<String, List<MigrationFile>> loadModules() {
        final Map<String, List<MigrationFile>> modules = new TreeMap<>();
        scriptsByModule().forEach((module, scripts) -> modules.put(module, parseModule(module, scripts)));
        return modules;
    }

    private static List<MigrationFile> parseModule(final String module, final Map<String, String> scripts) {
        if (!SchemaHistory.isValidModuleName(module)) {
            throw new IllegalArgumentException("Invalid module name: " + module);
        }

        final List<MigrationFile> migrations = new ArrayList<>();
        scripts.forEach((script, content) -> migrations.add(MigrationFile.parse(script, content)));
        migrations.sort(Comparator.comparing(MigrationFile::number));

        for (int index = 1; index < migrations.size(); index++) {
            final MigrationFile previous = migrations.get(index - 1);
            final MigrationFile current = migrations.get(index);
            if (previous.number().equals(current.number())) {
                throw new IllegalStateException("Module '" + module + "' has two migrations with version "
                        + current.number() + ": " + previous.script() + " and " + current.script() + ".");
            }
        }

        return migrations;
    }

    private static void validateModule(
            final String module, final Map<String, String> scripts, final List<MigrationProblem> problems) {
        if (!SchemaHistory.isValidModuleName(module)) {
            problems.add(MigrationProblem.error(module, "Module name must match [a-z0-9_]+."));
        }

        final Map<BigInteger, String> scriptByVersion = new HashMap<>();
        scripts.forEach((script, content) -> {
            final MigrationFile migration;
            try {
                migration = MigrationFile.parse(script, content);
            } catch (final IllegalArgumentException e) {
                problems.add(MigrationProblem.error(module, e.getMessage()));
                return;
            }

            final String duplicate = scriptByVersion.putIfAbsent(migration.number(), script);
            if (duplicate != null) {
                problems.add(MigrationProblem.error(
                        module,
                        "Version " + migration.number() + " is used by both " + duplicate + " and " + script + "."));
            }

            if (!migration.hasDown()) {
                problems.add(MigrationProblem.warning(
                        module, "Migration " + script + " has no '-- +migrate down' section."));
            }
        });
    }

    private int migrateModule(final DataSource dataSource, final String module, final List<MigrationFile> migrations) {
        final SchemaHistory history = new SchemaHistory(tablePrefix, module);

        try (Connection connection = dataSource.getConnection()) {
            return applyUnderLock(connection, module, history, migrations);
        } catch (final SQLException e) {
            throw new IllegalStateException("Migration failed for module '" + module + "'", e);
        }
    }

    private static int applyUnderLock(
            final Connection connection,
            final String module,
            final SchemaHistory history,
            final List<MigrationFile> migrations)
            throws SQLException {
        connection.setAutoCommit(true);

        try (MigrationLock ignored = MigrationLock.acquire(connection, history.tableName())) {
            history.ensureTable(connection);
            final Map<String, String> applied = history.appliedChecksums(connection);
            int count = 0;

            for (final MigrationFile migration : migrations) {
                if (applyIfPending(connection, module, history, applied, migration)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static boolean applyIfPending(
            final Connection connection,
            final String module,
            final SchemaHistory history,
            final Map<String, String> applied,
            final MigrationFile migration)
            throws SQLException {
        final String appliedChecksum = applied.get(migration.version());
        if (appliedChecksum != null) {
            verifyUnchanged(module, migration, appliedChecksum);
            return false;
        }

        applyOne(connection, module, history, migration);
        return true;
    }

    private static void verifyUnchanged(
            final String module, final MigrationFile migration, final String appliedChecksum) {
        if (!appliedChecksum.equals(migration.checksum())) {
            throw new IllegalStateException("Migration " + migration.script() + " in module '" + module
                    + "' was modified after it was applied (checksum mismatch); "
                    + "migrations are immutable once applied.");
        }
    }

    private static void applyOne(
            final Connection connection,
            final String module,
            final SchemaHistory history,
            final MigrationFile migration)
            throws SQLException {
        connection.setAutoCommit(false);
        boolean committed = false;

        try {
            execute(connection, migration.upSql());
            history.record(connection, migration);
            connection.commit();
            committed = true;
            LOGGER.info("Applied {} (module '{}').", migration.script(), module);
        } finally {
            if (!committed) {
                connection.rollback();
            }
            connection.setAutoCommit(true);
        }
    }

    private void statusModule(final DataSource dataSource, final String module, final List<MigrationFile> migrations) {
        final SchemaHistory history = new SchemaHistory(tablePrefix, module);
        try (Connection connection = dataSource.getConnection()) {
            final Map<String, String> applied =
                    history.exists(connection) ? history.appliedChecksums(connection) : Map.of();
            LOGGER.info("Module '{}':", module);

            for (final MigrationFile migration : migrations) {
                final String state = applied.containsKey(migration.version()) ? "[applied]" : "[pending]";
                LOGGER.info("  {} V{} — {}", state, migration.version(), migration.script());
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Status failed for module '" + module + "'", e);
        }
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public static final class Builder {

        private MigrationSource source;
        private String layoutPattern = DEFAULT_LAYOUT;
        private String historyTablePrefix = DEFAULT_TABLE_PREFIX;

        private Builder() {}

        public Builder classpath(final String basePackage) {
            this.source = new ClasspathSource(basePackage.replace('.', '/'));
            return this;
        }

        public Builder directory(final Path directory) {
            this.source = new DirectorySource(directory);
            return this;
        }

        public Builder location(final String location) {
            if (location.startsWith(FILESYSTEM_PREFIX)) {
                return directory(Path.of(location.substring(FILESYSTEM_PREFIX.length())));
            }

            if (location.startsWith(CLASSPATH_PREFIX)) {
                this.source = new ClasspathSource(location.substring(CLASSPATH_PREFIX.length()));
                return this;
            }

            return classpath(location);
        }

        public Builder layout(final String layout) {
            this.layoutPattern = layout;
            return this;
        }

        public Builder tablePrefix(final String tablePrefix) {
            this.historyTablePrefix = tablePrefix;
            return this;
        }

        public MigrationEngine build() {
            if (source == null) {
                throw new IllegalStateException("No migration location: call classpath(), directory() or location().");
            }

            if (!SchemaHistory.isValidTablePrefix(historyTablePrefix)) {
                throw new IllegalArgumentException(
                        "Invalid table prefix '" + historyTablePrefix + "': must match [a-z_][a-z0-9_]*.");
            }

            return new MigrationEngine(source, new MigrationLayout(layoutPattern), historyTablePrefix);
        }
    }
}

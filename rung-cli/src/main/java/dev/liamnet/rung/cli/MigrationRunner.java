package dev.liamnet.rung.cli;

import dev.liamnet.rung.MigrationEngine;
import dev.liamnet.rung.MigrationProblem;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "rung",
        description = "Applies, rolls back, inspects, and validates per-module database migrations.",
        mixinStandardHelpOptions = true,
        subcommands = {
            MigrationRunner.Migrate.class,
            MigrationRunner.Rollback.class,
            MigrationRunner.Status.class,
            MigrationRunner.Validate.class
        })
public final class MigrationRunner implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(final String[] args) {
        System.exit(new CommandLine(new MigrationRunner()).execute(args));
    }

    public static final class SourceOptions {

        @Option(names = "--dir", required = true, description = "Root directory to scan for migrations.")
        Path dir;

        @Option(
                names = "--layout",
                defaultValue = MigrationEngine.DEFAULT_LAYOUT,
                description = "Where a module's migrations sit under the root (default: ${DEFAULT-VALUE}).")
        String layout;

        @Option(
                names = "--module-order",
                split = ",",
                description = "Modules to migrate first, in this order; the rest follow alphabetically.")
        String[] moduleOrder = {};

        public MigrationEngine.Builder engine() {
            return MigrationEngine.builder().directory(dir).layout(layout).moduleOrder(moduleOrder);
        }
    }

    public static final class DatabaseOptions {

        @Option(names = "--url", description = "JDBC URL (defaults to $DATABASE_URL).")
        String url;

        @Option(names = "--user", description = "Database user (defaults to $DATABASE_USER).")
        String user;

        @Option(names = "--password", description = "Database password (defaults to $DATABASE_PASSWORD).")
        String password;

        @Option(
                names = "--table-prefix",
                defaultValue = MigrationEngine.DEFAULT_TABLE_PREFIX,
                description = "Prefix of the per-module history tables (default: ${DEFAULT-VALUE}).")
        String tablePrefix;

        public DataSource dataSource() {
            return DbConfig.resolve(url, user, password, System.getenv());
        }
    }

    @Command(name = "migrate", description = "Apply all pending forward migrations.")
    public static final class Migrate implements Callable<Integer> {

        @Mixin
        SourceOptions source;

        @Mixin
        DatabaseOptions database;

        @Override
        public Integer call() {
            source.engine().tablePrefix(database.tablePrefix).build().migrate(database.dataSource());
            return 0;
        }
    }

    @Command(name = "status", description = "Show applied/pending migrations per module.")
    public static final class Status implements Callable<Integer> {

        @Mixin
        SourceOptions source;

        @Mixin
        DatabaseOptions database;

        @Override
        public Integer call() {
            source.engine().tablePrefix(database.tablePrefix).build().status(database.dataSource());
            return 0;
        }
    }

    @Command(name = "rollback", description = "Roll back the last migration(s) for a module.")
    public static final class Rollback implements Callable<Integer> {

        @Mixin
        SourceOptions source;

        @Mixin
        DatabaseOptions database;

        @Parameters(index = "0", description = "Module to roll back.")
        String module;

        @Parameters(index = "1", arity = "0..1", defaultValue = "1", description = "Number of steps (default 1).")
        int steps;

        @Override
        public Integer call() {
            source.engine().tablePrefix(database.tablePrefix).build().rollback(database.dataSource(), module, steps);
            return 0;
        }
    }

    @Command(name = "validate", description = "Check migration file names and structure without touching a database.")
    public static final class Validate implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Mixin
        SourceOptions source;

        @Option(names = "--strict", description = "Treat warnings (such as a missing down section) as failures.")
        boolean strict;

        @Override
        public Integer call() {
            final MigrationEngine engine = source.engine().build();
            final List<MigrationProblem> problems = engine.validate();
            final boolean failed = problems.stream().anyMatch(problem -> strict || problem.isError());

            problems.forEach(problem -> print(problem.describe()));
            print((failed ? "Validation failed" : "Validation passed") + ": "
                    + engine.discoverModules().size() + " module(s), " + problems.size() + " problem(s).");
            return failed ? 1 : 0;
        }

        private void print(final String line) {
            spec.commandLine().getOut().println(line);
        }
    }
}

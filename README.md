# Rung

SQL migration engine for Java where every module owns its own migrations and its own history.

- Migrations live with the module they belong to, by default in `<module>/migrations/`.
- Each module gets its own history table (`rung_migrations_<module>`), so modules migrate and roll back independently.
- Up and down SQL sit in the same file.
- Applied migrations are immutable: a changed up section fails the next run with a checksum mismatch.
- On PostgreSQL, each module is migrated under an advisory lock, so concurrent starts are safe.

| Module | What it is | Depends on |
|---|---|---|
| `rung-core` | The engine, for any Java app | `slf4j-api` only |
| `rung-spring-boot-starter` | Runs migrations on startup in a Spring Boot app | `rung-core`, Spring Boot |
| `rung-cli` | The `rung` command | `rung-core`, picocli, the PostgreSQL driver |

## Install

Requires Java 17 or newer. Built for and tested on PostgreSQL; other databases are untested.

```kotlin
implementation("dev.liamnet:rung-core:1.0.0")
// or, in a Spring Boot app:
implementation("dev.liamnet:rung-spring-boot-starter:1.0.0")
```

The CLI is attached to each [GitHub release](https://github.com/LiamDev06/rung/releases) as a zip.

## Migration files

Named `V<number>__<description>.sql`. The number can be a sequence (`V001`) or a timestamp (`V20261113120000`).

```sql
-- +migrate up
CREATE TABLE widget (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE widget;
```

The down section is optional, but a migration without one cannot be rolled back. Each migration runs in its own transaction. Files prefixed with `_` are drafts and are skipped. Module names must match `[a-z0-9_]+`.

## Where migrations live

Two settings decide this: a **location** (the root to scan) and a **layout** (where a module's files sit under that root). The layout must contain `{module}` exactly once.

Next to the code, which is the default layout `{module}/migrations`:

```
src/main/java/com/example/app/      <- location: com.example.app
  user/migrations/V001__create_user.sql
  billing/migrations/V001__create_invoice.sql
```

In a central resources folder, with layout `{module}`:

```
src/main/resources/db/migrations/   <- location: classpath:db/migrations
  user/V001__create_user.sql
  billing/V001__create_invoice.sql
```

Gradle and Maven only copy `.java` files out of `src/main/java`, so if you keep SQL next to the code, add it to the resources. In Gradle:

```kotlin
tasks.named<ProcessResources>("processResources") {
    from("src/main/java") { include("**/migrations/**/*.sql") }
}
```

## Plain Java

```java
MigrationEngine.forClasspath("com.example.app").migrate(dataSource);
```

With options:

```java
MigrationEngine engine = MigrationEngine.builder()
        .location("classpath:db/migrations")   // or .classpath("com.example.app") / .directory(path)
        .layout("{module}")
        .tablePrefix("schema_history_")
        .build();

engine.migrate(dataSource);
engine.rollback(dataSource, "user", 1);
engine.status(dataSource);
```

A location is `classpath:<path>`, `filesystem:<path>`, or a bare package name. `SimpleDataSource` is included for apps without a connection pool.

The public API is `MigrationEngine`, `MigrationProblem` and `SimpleDataSource`. Everything under `dev.liamnet.rung.internal` is implementation detail and can change in any release.

## Spring Boot

Add `rung-spring-boot-starter`. With no configuration it scans the package of your `@SpringBootApplication` class and migrates on startup, before JPA, `JdbcTemplate` and other database-dependent beans are created.

```properties
rung.enabled=true
rung.location=classpath:db/migrations
rung.layout={module}
rung.table-prefix=rung_migrations_
```

All four are optional. Define your own `MigrationEngine` bean to replace the configured one.

## Validating migrations

`validate` checks files without a database:

- file names that are not `V<number>__<description>.sql`
- files with no `-- +migrate up` section
- two files in a module sharing a version (`V1` and `V001` count as the same)
- module names that are not `[a-z0-9_]+`
- missing `-- +migrate down` sections, reported as warnings

From the CLI, `rung validate --dir <root>` exits non-zero on errors, and on warnings too with `--strict`. In a test:

```java
assertThat(MigrationEngine.forClasspath("com.example.app").validate()).isEmpty();
```

`migrate` and `status` also refuse to run when a module has a misnamed file or a duplicate version.

## CLI

```
./gradlew :rung-cli:installDist
alias rung=rung-cli/build/install/rung/bin/rung

rung migrate  --dir src/main/java/com/example/app
rung status   --dir src/main/java/com/example/app
rung rollback user 2 --dir src/main/java/com/example/app
rung validate --dir src/main/java/com/example/app --strict
```

`--layout` and `--table-prefix` match the options above. The connection comes from `--url`, `--user` and `--password`, falling back to `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`.

## Development

```
./gradlew build   # compile, test, checkstyle, pmd, spotless
./gradlew lint
```

Building requires JDK 25; the published artifacts target Java 17.

## License

Apache License 2.0. See [LICENSE](LICENSE).

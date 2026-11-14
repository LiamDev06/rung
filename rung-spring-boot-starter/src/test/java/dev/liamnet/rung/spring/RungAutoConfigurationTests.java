package dev.liamnet.rung.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.liamnet.rung.MigrationEngine;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RungAutoConfigurationTests {

    private static final String FIXTURES = "rung.location=classpath:dev/liamnet/rung/spring";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, RungAutoConfiguration.class));

    @Test
    void migratesOnStartupFromTheConfiguredLocation() {
        runner.withPropertyValues(database("configured"), FIXTURES).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(rowCount(context.getBean(DataSource.class), "shop_item")).isZero();
            assertThat(rowCount(context.getBean(DataSource.class), "rung_migrations_shop"))
                    .isEqualTo(1);
        });
    }

    @Test
    void defaultsTheLocationToTheApplicationPackage() {
        runner.withUserConfiguration(Application.class)
                .withPropertyValues(database("inferred"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(rowCount(context.getBean(DataSource.class), "rung_migrations_shop"))
                            .isEqualTo(1);
                });
    }

    @Test
    void appliesLayoutAndTablePrefixProperties() {
        runner.withPropertyValues(
                        database("customised"),
                        "rung.location=classpath:db/rung",
                        "rung.layout={module}",
                        "rung.table-prefix=history_")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(rowCount(context.getBean(DataSource.class), "central_item"))
                            .isZero();
                    assertThat(rowCount(context.getBean(DataSource.class), "history_shop"))
                            .isEqualTo(1);
                });
    }

    @Test
    void appliesTheModuleOrderProperty() {
        runner.withPropertyValues(
                        database("module_order"),
                        "rung.location=classpath:db/ordered",
                        "rung.layout={module}",
                        "rung.module-order=player")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(rowCount(context.getBean(DataSource.class), "rung_migrations_player"))
                            .isEqualTo(1);
                    assertThat(rowCount(context.getBean(DataSource.class), "rung_migrations_badge"))
                            .isEqualTo(1);
                });
    }

    @Test
    void migratesAlphabeticallyWithoutTheModuleOrderProperty() {
        runner.withPropertyValues(
                        database("no_module_order"), "rung.location=classpath:db/ordered", "rung.layout={module}")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Migration failed for module 'badge'"));
    }

    @Test
    void beansThatDependOnTheDatabaseWaitForMigrations() {
        runner.withUserConfiguration(ProbeConfiguration.class)
                .withPropertyValues(database("ordering"), FIXTURES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TableProbe.class).rows()).isZero();
                });
    }

    @Test
    void doesNothingWhenDisabled() {
        runner.withPropertyValues(database("disabled"), FIXTURES, "rung.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(MigrationEngine.class)
                        .doesNotHaveBean(RungMigrationInitializer.class));
    }

    @Test
    void failsWithGuidanceWhenNoLocationCanBeInferred() {
        runner.withPropertyValues(database("unresolved"))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("set 'rung.location'"));
    }

    private static String database(final String name) {
        return "spring.datasource.url=jdbc:h2:mem:starter_" + name
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static int rowCount(final DataSource dataSource, final String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class Application {}

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        @DependsOnDatabaseInitialization
        TableProbe tableProbe(final DataSource dataSource) throws SQLException {
            return new TableProbe(rowCount(dataSource, "shop_item"));
        }
    }

    record TableProbe(int rows) {}
}

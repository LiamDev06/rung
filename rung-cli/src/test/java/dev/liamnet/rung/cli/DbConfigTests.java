package dev.liamnet.rung.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.liamnet.rung.SimpleDataSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbConfigTests {

    private static final Map<String, String> ENVIRONMENT = Map.of("DATABASE_URL", "jdbc:postgresql://env/db");

    @Test
    void explicitUrlFlagWinsOverTheEnvironment() {
        final SimpleDataSource dataSource = DbConfig.resolve("jdbc:postgresql://flag/db", "user", "pass", ENVIRONMENT);

        assertThat(dataSource.jdbcUrl()).isEqualTo("jdbc:postgresql://flag/db");
    }

    @Test
    void urlFallsBackToTheEnvironment() {
        final SimpleDataSource dataSource = DbConfig.resolve(null, null, null, ENVIRONMENT);

        assertThat(dataSource.jdbcUrl()).isEqualTo("jdbc:postgresql://env/db");
    }

    @Test
    void blankFlagFallsBackToTheEnvironment() {
        final SimpleDataSource dataSource = DbConfig.resolve(" ", null, null, ENVIRONMENT);

        assertThat(dataSource.jdbcUrl()).isEqualTo("jdbc:postgresql://env/db");
    }

    @Test
    void missingUrlFailsWithGuidance() {
        assertThatThrownBy(() -> DbConfig.resolve(null, null, null, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--url");
    }
}

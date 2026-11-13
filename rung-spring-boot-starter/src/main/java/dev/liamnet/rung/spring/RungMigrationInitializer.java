package dev.liamnet.rung.spring;

import dev.liamnet.rung.MigrationEngine;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;

public class RungMigrationInitializer implements InitializingBean {

    private final MigrationEngine engine;
    private final DataSource dataSource;

    public RungMigrationInitializer(final MigrationEngine engine, final DataSource dataSource) {
        this.engine = engine;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        engine.migrate(dataSource);
    }
}

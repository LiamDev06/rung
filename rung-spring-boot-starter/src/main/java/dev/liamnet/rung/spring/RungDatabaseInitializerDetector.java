package dev.liamnet.rung.spring;

import java.util.Set;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

// Makes JPA, JdbcTemplate and other database-dependent beans wait until migrations have run.
public class RungDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(RungMigrationInitializer.class);
    }
}

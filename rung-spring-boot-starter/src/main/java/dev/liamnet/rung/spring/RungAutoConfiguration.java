package dev.liamnet.rung.spring;

import dev.liamnet.rung.MigrationEngine;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        })
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "rung", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RungProperties.class)
@Import(DatabaseInitializationDependencyConfigurer.class)
public class RungAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MigrationEngine rungMigrationEngine(final RungProperties properties, final BeanFactory beanFactory) {
        return MigrationEngine.builder()
                .location(resolveLocation(properties, beanFactory))
                .layout(properties.layout())
                .tablePrefix(properties.tablePrefix())
                .moduleOrder(properties.moduleOrder().toArray(String[]::new))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RungMigrationInitializer rungMigrationInitializer(
            final MigrationEngine engine, final DataSource dataSource) {
        return new RungMigrationInitializer(engine, dataSource);
    }

    private static String resolveLocation(final RungProperties properties, final BeanFactory beanFactory) {
        if (properties.location() != null && !properties.location().isBlank()) {
            return properties.location();
        }

        if (AutoConfigurationPackages.has(beanFactory)) {
            final List<String> packages = AutoConfigurationPackages.get(beanFactory);
            if (!packages.isEmpty()) {
                return packages.get(0);
            }
        }

        throw new IllegalStateException("Rung could not infer where migrations live: set 'rung.location', for example"
                + " 'classpath:db/migrations' or your application's base package.");
    }
}

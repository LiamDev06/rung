package dev.liamnet.rung.spring;

import dev.liamnet.rung.MigrationEngine;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("rung")
public record RungProperties(
        @DefaultValue("true") boolean enabled,
        String location,
        @DefaultValue(MigrationEngine.DEFAULT_LAYOUT) String layout,
        @DefaultValue(MigrationEngine.DEFAULT_TABLE_PREFIX) String tablePrefix,
        @DefaultValue List<String> moduleOrder) {}

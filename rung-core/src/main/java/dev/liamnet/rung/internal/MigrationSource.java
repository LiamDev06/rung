package dev.liamnet.rung.internal;

import java.util.Map;

public interface MigrationSource {

    Map<String, String> loadSqlFiles();
}

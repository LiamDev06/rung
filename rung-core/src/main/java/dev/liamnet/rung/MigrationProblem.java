package dev.liamnet.rung;

public record MigrationProblem(Severity severity, String module, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static MigrationProblem error(final String module, final String message) {
        return new MigrationProblem(Severity.ERROR, module, message);
    }

    public static MigrationProblem warning(final String module, final String message) {
        return new MigrationProblem(Severity.WARNING, module, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String describe() {
        return (isError() ? "error" : "warning") + " [" + module + "] " + message;
    }
}

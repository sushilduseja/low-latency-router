package com.trading;

public enum ExecutionMode {
    STANDARD("standard"),
    ZERO_GC("zerogc");

    private final String cliName;

    ExecutionMode(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }

    public static ExecutionMode fromCli(String rawValue) {
        for (ExecutionMode mode : values()) {
            if (mode.cliName.equalsIgnoreCase(rawValue)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode: " + rawValue);
    }
}

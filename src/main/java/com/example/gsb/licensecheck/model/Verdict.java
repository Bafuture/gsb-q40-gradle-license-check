package com.example.gsb.licensecheck.model;

public enum Verdict {
    ALLOWED,
    DENIED,
    UNKNOWN;

    public String displayName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

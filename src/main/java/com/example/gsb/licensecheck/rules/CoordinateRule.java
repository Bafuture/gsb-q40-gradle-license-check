package com.example.gsb.licensecheck.rules;

public final class CoordinateRule {
    private final String pattern;
    private final CoordinateDecision decision;
    private final String reason;

    public CoordinateRule(String pattern, CoordinateDecision decision, String reason) {
        this.pattern = pattern;
        this.decision = decision;
        this.reason = reason;
    }

    public String getPattern() {
        return pattern;
    }

    public CoordinateDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }
}

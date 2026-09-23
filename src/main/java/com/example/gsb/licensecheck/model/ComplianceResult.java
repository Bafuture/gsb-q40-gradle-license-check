package com.example.gsb.licensecheck.model;

import java.util.List;

public final class ComplianceResult {
    private final List<ComponentReport> components;
    private final List<String> violations;

    public ComplianceResult(List<ComponentReport> components, List<String> violations) {
        this.components = List.copyOf(components);
        this.violations = List.copyOf(violations);
    }

    public List<ComponentReport> getComponents() {
        return components;
    }

    public List<String> getViolations() {
        return violations;
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }
}

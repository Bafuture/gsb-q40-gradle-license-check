package com.example.gsb.licensecheck.model;

import java.util.List;

public final class ComponentReport {
    private final String group;
    private final String name;
    private final String version;
    private final List<DeclaredLicense> licenses;
    private final Verdict verdict;
    private final String matchedRule;
    private final String licenseSource;

    public ComponentReport(String group,
                           String name,
                           String version,
                           List<DeclaredLicense> licenses,
                           Verdict verdict,
                           String matchedRule,
                           String licenseSource) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.licenses = List.copyOf(licenses);
        this.verdict = verdict;
        this.matchedRule = matchedRule;
        this.licenseSource = licenseSource;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getCoordinates() {
        return group + ':' + name + ':' + version;
    }

    public List<DeclaredLicense> getLicenses() {
        return licenses;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public String getLicenseSource() {
        return licenseSource;
    }
}

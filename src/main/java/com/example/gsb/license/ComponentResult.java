package com.example.gsb.license;

import java.util.List;

/**
 * Resolved license information and compliance verdict for one component.
 */
public class ComponentResult {
    private final String group;
    private final String name;
    private final String version;
    private final List<LicenseInfo> licenses;
    private final Verdict verdict;
    private final String matchedRule;

    public ComponentResult(String group, String name, String version,
                           List<LicenseInfo> licenses, Verdict verdict, String matchedRule) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.licenses = licenses;
        this.verdict = verdict;
        this.matchedRule = matchedRule;
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
        return group + ":" + name + ":" + version;
    }

    public List<LicenseInfo> getLicenses() {
        return licenses;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public boolean isViolation() {
        return verdict == Verdict.DENIED || verdict == Verdict.UNKNOWN;
    }
}

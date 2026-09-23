package com.example.gsb.licensecheck.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleSet {
    private final List<String> allowedLicenses;
    private final List<String> deniedLicenses;
    private final Map<String, String> allowCoordinates;
    private final Map<String, String> denyCoordinates;

    public RuleSet(List<String> allowedLicenses,
                   List<String> deniedLicenses,
                   Map<String, String> allowCoordinates,
                   Map<String, String> denyCoordinates) {
        this.allowedLicenses = new ArrayList<>(allowedLicenses);
        this.deniedLicenses = new ArrayList<>(deniedLicenses);
        this.allowCoordinates = new LinkedHashMap<>(allowCoordinates);
        this.denyCoordinates = new LinkedHashMap<>(denyCoordinates);
    }

    public List<String> getAllowedLicenses() {
        return allowedLicenses;
    }

    public List<String> getDeniedLicenses() {
        return deniedLicenses;
    }

    public Map<String, String> getAllowCoordinates() {
        return allowCoordinates;
    }

    public Map<String, String> getDenyCoordinates() {
        return denyCoordinates;
    }
}

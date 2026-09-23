package com.example.gsb.license;

import org.gradle.api.Named;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-component rule overriding the global allow/deny lists.
 *
 * <p>The {@link #getName()} is the coordinate key, either {@code group:name}
 * (matches every version) or {@code group:name:version}.
 */
public class CoordinateRule implements Named {

    private final String name;
    private final List<String> allowLicenses = new ArrayList<>();
    private final List<String> denyLicenses = new ArrayList<>();
    private boolean allowUnknown = false;

    @Inject
    public CoordinateRule(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<String> getAllowLicenses() {
        return allowLicenses;
    }

    public void allow(String license) {
        allowLicenses.add(license);
    }

    public List<String> getDenyLicenses() {
        return denyLicenses;
    }

    public void deny(String license) {
        denyLicenses.add(license);
    }

    public boolean isAllowUnknown() {
        return allowUnknown;
    }

    public void allowUnknown(boolean value) {
        this.allowUnknown = value;
    }
}

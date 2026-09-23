package com.example.gsb.license;

import java.util.List;

/**
 * A runtime dependency component with the licenses extracted from its metadata.
 *
 * <p>When no license metadata can be read, {@code licenses} is empty; the
 * component must still be reported and marked UNKNOWN rather than skipped.
 */
public class ResolvedComponent {
    private final String group;
    private final String name;
    private final String version;
    private final List<LicenseInfo> licenses;

    public ResolvedComponent(String group, String name, String version, List<LicenseInfo> licenses) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.licenses = licenses;
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

    public List<LicenseInfo> getLicenses() {
        return licenses;
    }
}

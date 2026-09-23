package com.example.gsb.license;

/**
 * A single license declared by a component, as read from its POM metadata.
 */
public class LicenseInfo {
    private final String name;
    private final String url;

    public LicenseInfo(String name, String url) {
        this.name = name == null ? "" : name.trim();
        this.url = url == null ? "" : url.trim();
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}

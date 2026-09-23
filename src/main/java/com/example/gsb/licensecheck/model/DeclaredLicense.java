package com.example.gsb.licensecheck.model;

import java.util.Objects;

public final class DeclaredLicense {
    private final String name;
    private final String url;

    public DeclaredLicense(String name, String url) {
        this.name = name == null ? "" : name.trim();
        this.url = url == null ? "" : url.trim();
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public boolean isEmpty() {
        return name.isEmpty() && url.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeclaredLicense that)) {
            return false;
        }
        return name.equals(that.name) && url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url);
    }
}

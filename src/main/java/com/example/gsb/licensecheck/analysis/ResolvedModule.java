package com.example.gsb.licensecheck.analysis;

public record ResolvedModule(String group, String name, String version) {

    public String coordinates() {
        return group + ':' + name + ':' + version;
    }
}

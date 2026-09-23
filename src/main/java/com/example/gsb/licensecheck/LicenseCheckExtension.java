package com.example.gsb.licensecheck;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class LicenseCheckExtension {

    @Inject
    public LicenseCheckExtension(ObjectFactory objects) {
        getLenient().convention(false);
        getTextReport().convention(true);
        getJsonReport().convention(true);
    }

    public abstract Property<Boolean> getLenient();

    public abstract ListProperty<String> getAllowedLicenses();

    public abstract ListProperty<String> getDeniedLicenses();

    public abstract MapProperty<String, String> getAllowCoordinates();

    public abstract MapProperty<String, String> getDenyCoordinates();

    public abstract DirectoryProperty getReportsDir();

    public abstract Property<Boolean> getTextReport();

    public abstract Property<Boolean> getJsonReport();

    public void allowLicense(String license) {
        getAllowedLicenses().add(license);
    }

    public void denyLicense(String license) {
        getDeniedLicenses().add(license);
    }

    public void allowCoordinate(String pattern) {
        allowCoordinate(pattern, "explicit coordinate allow-rule");
    }

    public void allowCoordinate(String pattern, String reason) {
        getAllowCoordinates().put(pattern, reason);
    }

    public void denyCoordinate(String pattern) {
        denyCoordinate(pattern, "explicit coordinate deny-rule");
    }

    public void denyCoordinate(String pattern, String reason) {
        getDenyCoordinates().put(pattern, reason);
    }
}

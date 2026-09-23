package com.example.gsb.license;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * DSL extension registered as {@code licenseCheck}.
 *
 * <pre>
 * licenseCheck {
 *     allowLicenses = ['Apache-2.0', 'MIT License']
 *     denyLicenses  = ['GNU General Public License, version 3']
 *     allowUnknown = false
 *     failOnError = true
 *
 *     rules {
 *         'com.special:widget' {
 *             allow 'GPL-3.0-with-classpath-exception'
 *         }
 *         'com.mystery:blob:1.0' {
 *             allowUnknown true
 *         }
 *     }
 * }
 * </pre>
 */
public class LicenseCheckExtension {

    private List<String> allowLicenses = new ArrayList<>();
    private List<String> denyLicenses = new ArrayList<>();
    private boolean allowUnknown = false;
    private boolean failOnError = true;
    private final NamedDomainObjectContainer<CoordinateRule> rules;

    @Inject
    public LicenseCheckExtension(ObjectFactory objects) {
        this.rules = objects.domainObjectContainer(CoordinateRule.class);
    }

    public List<String> getAllowLicenses() {
        return allowLicenses;
    }

    public void setAllowLicenses(List<String> allowLicenses) {
        this.allowLicenses = new ArrayList<>(allowLicenses);
    }

    public List<String> getDenyLicenses() {
        return denyLicenses;
    }

    public void setDenyLicenses(List<String> denyLicenses) {
        this.denyLicenses = new ArrayList<>(denyLicenses);
    }

    /** When true, components with unknown licenses are treated as allowed. */
    public boolean getAllowUnknown() {
        return allowUnknown;
    }

    public void setAllowUnknown(boolean allowUnknown) {
        this.allowUnknown = allowUnknown;
    }

    /** When true (default), deny hits or disallowed unknown licenses fail the build. */
    public boolean getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public NamedDomainObjectContainer<CoordinateRule> getRules() {
        return rules;
    }

    public void rules(org.gradle.api.Action<? super NamedDomainObjectContainer<CoordinateRule>> action) {
        action.execute(rules);
    }
}

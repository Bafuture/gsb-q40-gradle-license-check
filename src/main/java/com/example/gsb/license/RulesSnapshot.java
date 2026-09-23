package com.example.gsb.license;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable, serializable snapshot of the DSL rule configuration.
 *
 * <p>Used as a task input so changing any rule invalidates the build cache.
 */
public class RulesSnapshot implements Serializable {
    private final List<String> allowLicenses;
    private final List<String> denyLicenses;
    private final boolean allowUnknown;
    private final boolean failOnError;
    private final Map<String, CoordinateRuleData> coordinates;

    public RulesSnapshot(List<String> allowLicenses, List<String> denyLicenses,
                         boolean allowUnknown, boolean failOnError,
                         Map<String, CoordinateRule> coordinateRules) {
        this.allowLicenses = new ArrayList<>(allowLicenses);
        this.denyLicenses = new ArrayList<>(denyLicenses);
        this.allowUnknown = allowUnknown;
        this.failOnError = failOnError;
        this.coordinates = new TreeMap<>();
        for (Map.Entry<String, CoordinateRule> entry : coordinateRules.entrySet()) {
            CoordinateRule rule = entry.getValue();
            coordinates.put(entry.getKey(), new CoordinateRuleData(
                    new ArrayList<>(rule.getAllowLicenses()),
                    new ArrayList<>(rule.getDenyLicenses()),
                    rule.isAllowUnknown()));
        }
    }

    public List<String> getAllowLicenses() {
        return allowLicenses;
    }

    public List<String> getDenyLicenses() {
        return denyLicenses;
    }

    public boolean isAllowUnknown() {
        return allowUnknown;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public Map<String, CoordinateRuleData> getCoordinates() {
        return coordinates;
    }

    public static class CoordinateRuleData implements Serializable {
        private final List<String> allowLicenses;
        private final List<String> denyLicenses;
        private final boolean allowUnknown;

        public CoordinateRuleData(List<String> allowLicenses, List<String> denyLicenses,
                                  boolean allowUnknown) {
            this.allowLicenses = allowLicenses;
            this.denyLicenses = denyLicenses;
            this.allowUnknown = allowUnknown;
        }

        public List<String> getAllowLicenses() {
            return allowLicenses;
        }

        public List<String> getDenyLicenses() {
            return denyLicenses;
        }

        public boolean isAllowUnknown() {
            return allowUnknown;
        }
    }
}

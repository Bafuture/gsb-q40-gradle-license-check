package com.example.gsb.licensecheck.rules;

import com.example.gsb.licensecheck.analysis.ResolvedModule;
import com.example.gsb.licensecheck.model.DeclaredLicense;
import com.example.gsb.licensecheck.model.Verdict;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Applies the configured rules to one component.
 *
 * <p>Evaluation order for a component:</p>
 * <ol>
 *   <li>coordinate deny-rules (highest priority),</li>
 *   <li>coordinate allow-rules,</li>
 *   <li>license blacklist (any declared license on the blacklist denies),</li>
 *   <li>license whitelist (every declared license on the whitelist allows),</li>
 *   <li>otherwise the component is UNKNOWN.</li>
 * </ol>
 */
public final class RuleEvaluator {

    private final RuleSet ruleSet;

    public RuleEvaluator(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public RuleEvaluation evaluate(ResolvedModule module, List<DeclaredLicense> licenses) {
        String coordinates = module.coordinates();

        String denyPattern = firstMatchingPattern(coordinates, ruleSet.getDenyCoordinates().keySet());
        if (denyPattern != null) {
            return new RuleEvaluation(Verdict.DENIED,
                    describeCoordinateRule("coordinate deny-rule", denyPattern,
                            ruleSet.getDenyCoordinates().get(denyPattern)));
        }

        String allowPattern = firstMatchingPattern(coordinates, ruleSet.getAllowCoordinates().keySet());
        if (allowPattern != null) {
            return new RuleEvaluation(Verdict.ALLOWED,
                    describeCoordinateRule("coordinate allow-rule", allowPattern,
                            ruleSet.getAllowCoordinates().get(allowPattern)));
        }

        if (licenses.isEmpty()) {
            return new RuleEvaluation(Verdict.UNKNOWN, "no license metadata found");
        }

        for (DeclaredLicense license : licenses) {
            String denied = firstMatchingLicense(license, ruleSet.getDeniedLicenses());
            if (denied != null) {
                return new RuleEvaluation(Verdict.DENIED,
                        "license blacklist: '" + denied + "'");
            }
        }

        for (DeclaredLicense license : licenses) {
            if (firstMatchingLicense(license, ruleSet.getAllowedLicenses()) == null) {
                return new RuleEvaluation(Verdict.UNKNOWN,
                        "license '" + display(license) + "' not on the whitelist");
            }
        }
        return new RuleEvaluation(Verdict.ALLOWED, "license whitelist");
    }

    private static String describeCoordinateRule(String kind, String pattern, String reason) {
        String text = kind + " '" + pattern + "'";
        if (reason != null && !reason.isBlank()) {
            text += " (" + reason + ")";
        }
        return text;
    }

    private static String firstMatchingLicense(DeclaredLicense license, List<String> configured) {
        String name = normalize(license.getName());
        String url = normalize(license.getUrl());
        for (String candidate : configured) {
            String normalized = normalize(candidate);
            if (!normalized.isEmpty() && (normalized.equals(name) || normalized.equals(url))) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstMatchingPattern(String coordinates, Iterable<String> patterns) {
        for (String pattern : patterns) {
            if (coordinateMatches(coordinates, pattern)) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * Matches {@code group:name:version} coordinates against a pattern that may use
     * trailing wildcard segments ({@code *}) to match any value.
     * Examples: {@code com.example:foo:1.0}, {@code com.example:foo:*}, {@code com.example:*:*}.
     */
    static boolean coordinateMatches(String coordinates, String pattern) {
        String[] actual = coordinates.split(":", -1);
        String[] expected = pattern.split(":", -1);
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            String segment = expected[i].trim();
            if (segment.equals("*")) {
                continue;
            }
            if (segment.contains("*")) {
                String regex = Pattern.quote(segment).replace("*", "\\E.*\\Q");
                if (!Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(actual[i]).matches()) {
                    return false;
                }
            } else if (!segment.equalsIgnoreCase(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String display(DeclaredLicense license) {
        if (!license.getName().isEmpty()) {
            return license.getName();
        }
        return license.getUrl();
    }
}

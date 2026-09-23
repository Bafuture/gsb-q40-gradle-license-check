package com.example.gsb.license;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies global and per-coordinate rules to resolved components.
 *
 * <p>Semantics:
 * <ul>
 *   <li>A per-coordinate rule ({@code group:name:version}, falling back to
 *       {@code group:name}) fully replaces the global allow/deny lists and
 *       {@code allowUnknown} flag for that component.</li>
 *   <li>License matching is an exact, case-sensitive match against the license
 *       name declared in the POM (surrounding whitespace trimmed).</li>
 *   <li>Deny wins over allow: if any declared license is on the deny list the
 *       verdict is DENIED, even when another license is allowed.</li>
 *   <li>A component without usable license metadata is UNKNOWN; it becomes
 *       allowed only when an effective {@code allowUnknown} rule applies.</li>
 *   <li>Recognized licenses matching neither list get UNLISTED.</li>
 * </ul>
 */
public class RuleEngine {

    private static final String UNKNOWN_RULE = "unknown-license";

    public ComponentResult evaluate(ResolvedComponent component, RulesSnapshot rules) {
        RulesSnapshot.CoordinateRuleData override = findOverride(component, rules);
        List<String> allow;
        List<String> deny;
        boolean allowUnknown;
        String ruleSource;
        if (override != null) {
            allow = override.getAllowLicenses();
            deny = override.getDenyLicenses();
            allowUnknown = override.isAllowUnknown();
            ruleSource = "coordinate-rule:" + overrideKey(component, rules);
        } else {
            allow = rules.getAllowLicenses();
            deny = rules.getDenyLicenses();
            allowUnknown = rules.isAllowUnknown();
            ruleSource = "global";
        }

        List<LicenseInfo> licenses = component.getLicenses();
        boolean hasKnownLicense = licenses.stream().anyMatch(it -> !it.getName().isEmpty());

        if (!hasKnownLicense) {
            if (allowUnknown) {
                return result(component, Verdict.ALLOWED, ruleSource + ":allow-unknown");
            }
            return result(component, Verdict.UNKNOWN, UNKNOWN_RULE);
        }

        List<String> names = new ArrayList<>();
        for (LicenseInfo license : licenses) {
            if (!license.getName().isEmpty()) {
                names.add(license.getName());
            }
        }

        for (String name : names) {
            if (deny.contains(name)) {
                return result(component, Verdict.DENIED, ruleSource + ":deny:" + name);
            }
        }
        for (String name : names) {
            if (allow.contains(name)) {
                return result(component, Verdict.ALLOWED, ruleSource + ":allow:" + name);
            }
        }
        return result(component, Verdict.UNLISTED, null);
    }

    private RulesSnapshot.CoordinateRuleData findOverride(ResolvedComponent component, RulesSnapshot rules) {
        Map<String, RulesSnapshot.CoordinateRuleData> coordinates = rules.getCoordinates();
        RulesSnapshot.CoordinateRuleData exact =
                coordinates.get(component.getGroup() + ":" + component.getName() + ":" + component.getVersion());
        if (exact != null) {
            return exact;
        }
        return coordinates.get(component.getGroup() + ":" + component.getName());
    }

    private String overrideKey(ResolvedComponent component, RulesSnapshot rules) {
        Map<String, RulesSnapshot.CoordinateRuleData> coordinates = rules.getCoordinates();
        String gav = component.getGroup() + ":" + component.getName() + ":" + component.getVersion();
        if (coordinates.containsKey(gav)) {
            return gav;
        }
        return component.getGroup() + ":" + component.getName();
    }

    private ComponentResult result(ResolvedComponent component, Verdict verdict, String matchedRule) {
        return new ComponentResult(component.getGroup(), component.getName(), component.getVersion(),
                component.getLicenses(), verdict, matchedRule);
    }
}

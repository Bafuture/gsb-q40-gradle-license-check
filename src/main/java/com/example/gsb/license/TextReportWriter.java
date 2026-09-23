package com.example.gsb.license;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a human-readable license compliance report.
 */
public class TextReportWriter {

    public void write(Path output, List<ComponentResult> components, boolean failOnError) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("License Compliance Report\n");
        builder.append("=========================\n\n");

        long denied = components.stream().filter(c -> c.getVerdict() == Verdict.DENIED).count();
        long unknown = components.stream().filter(c -> c.getVerdict() == Verdict.UNKNOWN).count();
        long unlisted = components.stream().filter(c -> c.getVerdict() == Verdict.UNLISTED).count();
        long allowed = components.stream().filter(c -> c.getVerdict() == Verdict.ALLOWED).count();

        builder.append("Components : ").append(components.size()).append('\n');
        builder.append("Allowed    : ").append(allowed).append('\n');
        builder.append("Denied     : ").append(denied).append('\n');
        builder.append("Unknown    : ").append(unknown).append('\n');
        builder.append("Unlisted   : ").append(unlisted).append('\n');
        builder.append("Gate mode  : ").append(failOnError ? "fail on violations" : "warn only").append('\n');
        boolean violation = denied > 0 || unknown > 0;
        builder.append("Result     : ")
                .append(violation ? (failOnError ? "FAILED" : "PASSED WITH WARNINGS") : "PASSED")
                .append("\n\n");

        for (ComponentResult component : components) {
            builder.append(component.getCoordinates()).append('\n');
            if (component.getLicenses().isEmpty()) {
                builder.append("  Licenses  : ").append(LicenseCollector.UNKNOWN)
                        .append(" (no license metadata found)\n");
            } else {
                builder.append("  Licenses  : ");
                boolean first = true;
                for (LicenseInfo license : component.getLicenses()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    String name = license.getName().isEmpty() ? "(unnamed)" : license.getName();
                    builder.append(name);
                    if (!license.getUrl().isEmpty()) {
                        builder.append(" <").append(license.getUrl()).append('>');
                    }
                    first = false;
                }
                builder.append('\n');
            }
            builder.append("  Verdict   : ").append(component.getVerdict()).append('\n');
            String rule = component.getMatchedRule();
            builder.append("  Matched   : ").append(rule == null ? "-" : rule).append('\n');
            builder.append('\n');
        }

        if (denied > 0) {
            builder.append("Violations - blacklisted licenses:\n");
            components.stream()
                    .filter(c -> c.getVerdict() == Verdict.DENIED)
                    .forEach(c -> builder.append("  - ").append(c.getCoordinates())
                            .append(" [").append(c.getMatchedRule()).append("]\n"));
            builder.append('\n');
        }
        if (unknown > 0) {
            builder.append("Violations - unknown licenses:\n");
            components.stream()
                    .filter(c -> c.getVerdict() == Verdict.UNKNOWN)
                    .forEach(c -> builder.append("  - ").append(c.getCoordinates()).append('\n'));
            builder.append('\n');
        }

        Files.createDirectories(output.getParent());
        Files.write(output, builder.toString().getBytes(StandardCharsets.UTF_8));
    }
}

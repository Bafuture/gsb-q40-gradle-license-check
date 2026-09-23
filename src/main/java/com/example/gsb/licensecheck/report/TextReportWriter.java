package com.example.gsb.licensecheck.report;

import com.example.gsb.licensecheck.model.ComponentReport;
import com.example.gsb.licensecheck.model.ComplianceResult;
import com.example.gsb.licensecheck.model.DeclaredLicense;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class TextReportWriter {

    private TextReportWriter() {
    }

    public static void write(Path output, ComplianceResult result, boolean lenient) throws IOException {
        Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("Dependency License Compliance Report");
            writer.newLine();
            writer.write("=====================================");
            writer.newLine();
            writer.newLine();

            for (ComponentReport component : result.getComponents()) {
                writer.write("Component : " + component.getCoordinates());
                writer.newLine();
                writer.write("Licenses  : " + describeLicenses(component));
                writer.newLine();
                writer.write("Source    : " + component.getLicenseSource());
                writer.newLine();
                writer.write("Verdict   : " + component.getVerdict().displayName());
                writer.newLine();
                writer.write("Matched   : " + component.getMatchedRule());
                writer.newLine();
                writer.write("------------------------------------------------------------");
                writer.newLine();
            }

            writer.newLine();
            writer.write("Summary");
            writer.newLine();
            writer.write("-------");
            writer.newLine();
            writer.write("Total components : " + result.getComponents().size());
            writer.newLine();
            writer.write("Allowed          : "
                    + result.getComponents().stream().filter(c -> c.getVerdict().name().equals("ALLOWED")).count());
            writer.newLine();
            writer.write("Denied           : "
                    + result.getComponents().stream().filter(c -> c.getVerdict().name().equals("DENIED")).count());
            writer.newLine();
            writer.write("Unknown          : "
                    + result.getComponents().stream().filter(c -> c.getVerdict().name().equals("UNKNOWN")).count());
            writer.newLine();
            writer.newLine();

            if (result.getViolations().isEmpty()) {
                writer.write("Result: PASS - no license policy violations.");
                writer.newLine();
            } else {
                writer.write("Result: " + (lenient ? "WARN" : "FAIL")
                        + " - " + result.getViolations().size() + " violation(s):");
                writer.newLine();
                for (String violation : result.getViolations()) {
                    writer.write("  * " + violation);
                    writer.newLine();
                }
            }
        }
    }

    private static String describeLicenses(ComponentReport component) {
        if (component.getLicenses().isEmpty()) {
            return "UNKNOWN";
        }
        return component.getLicenses().stream()
                .map(TextReportWriter::describeOne)
                .collect(Collectors.joining(", "));
    }

    private static String describeOne(DeclaredLicense license) {
        if (!license.getName().isEmpty() && !license.getUrl().isEmpty()) {
            return license.getName() + " (" + license.getUrl() + ")";
        }
        if (!license.getName().isEmpty()) {
            return license.getName();
        }
        return license.getUrl();
    }
}

package com.example.gsb.licensecheck.report;

import com.example.gsb.licensecheck.model.ComponentReport;
import com.example.gsb.licensecheck.model.ComplianceResult;
import com.example.gsb.licensecheck.model.DeclaredLicense;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonReportWriter {

    private JsonReportWriter() {
    }

    public static void write(Path output, ComplianceResult result, boolean lenient) throws IOException {
        Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write('{');
            writer.newLine();
            writer.write("  \"status\": \"" + (result.hasViolations() ? (lenient ? "warn" : "fail") : "pass") + "\",");
            writer.newLine();
            writer.write("  \"lenient\": " + lenient + ",");
            writer.newLine();
            writer.write("  \"summary\": {");
            writer.newLine();
            writer.write("    \"total\": " + result.getComponents().size() + ",");
            writer.write("    \"allowed\": " + count(result, "ALLOWED") + ",");
            writer.write("    \"denied\": " + count(result, "DENIED") + ",");
            writer.write("    \"unknown\": " + count(result, "UNKNOWN"));
            writer.newLine();
            writer.write("  },");
            writer.newLine();
            writer.write("  \"components\": [");
            boolean first = true;
            for (ComponentReport component : result.getComponents()) {
                if (!first) {
                    writer.write(',');
                }
                first = false;
                writer.newLine();
                writer.write("    {");
                writer.newLine();
                writer.write("      \"group\": " + quote(component.getGroup()) + ",");
                writer.newLine();
                writer.write("      \"name\": " + quote(component.getName()) + ",");
                writer.newLine();
                writer.write("      \"version\": " + quote(component.getVersion()) + ",");
                writer.newLine();
                writer.write("      \"coordinates\": " + quote(component.getCoordinates()) + ",");
                writer.newLine();
                writer.write("      \"licenses\": [");
                boolean firstLicense = true;
                for (DeclaredLicense license : component.getLicenses()) {
                    if (!firstLicense) {
                        writer.write(", ");
                    }
                    firstLicense = false;
                    writer.write('{');
                    writer.write("\"name\": " + quote(license.getName()));
                    writer.write(", \"url\": " + quote(license.getUrl()));
                    writer.write('}');
                }
                writer.write("],");
                writer.newLine();
                writer.write("      \"verdict\": " + quote(component.getVerdict().displayName()) + ",");
                writer.newLine();
                writer.write("      \"matchedRule\": " + quote(component.getMatchedRule()) + ",");
                writer.newLine();
                writer.write("      \"licenseSource\": " + quote(component.getLicenseSource()));
                writer.newLine();
                writer.write("    }");
            }
            if (!first) {
                writer.newLine();
                writer.write("  ],");
            } else {
                writer.write("],");
            }
            writer.newLine();
            writer.write("  \"violations\": [");
            boolean firstViolation = true;
            for (String violation : result.getViolations()) {
                if (!firstViolation) {
                    writer.write(',');
                }
                firstViolation = false;
                writer.newLine();
                writer.write("    " + quote(violation));
            }
            if (!firstViolation) {
                writer.newLine();
                writer.write("  ]");
            } else {
                writer.write(']');
            }
            writer.newLine();
            writer.write('}');
            writer.newLine();
        }
    }

    private static long count(ComplianceResult result, String verdict) {
        return result.getComponents().stream()
                .filter(component -> component.getVerdict().name().equals(verdict))
                .count();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            builder.append(String.format("\\u%04x", (int) character));
                        } else {
                            builder.append(character);
                        }
                    }
                }
            }
        }
        return builder.append('"').toString();
    }
}

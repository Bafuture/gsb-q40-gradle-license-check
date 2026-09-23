package com.example.gsb.license;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a machine-readable JSON license compliance report.
 *
 * <p>Hand-written JSON to avoid adding a runtime dependency to the plugin.
 */
public class JsonReportWriter {

    public void write(Path output, List<ComponentResult> components, boolean failOnError) throws IOException {
        long denied = components.stream().filter(c -> c.getVerdict() == Verdict.DENIED).count();
        long unknown = components.stream().filter(c -> c.getVerdict() == Verdict.UNKNOWN).count();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"gateMode\": ").append(failOnError ? "\"fail\"" : "\"warn\"").append(",\n");
        json.append("  \"violations\": ").append(denied + unknown).append(",\n");
        json.append("  \"components\": [\n");
        for (int i = 0; i < components.size(); i++) {
            ComponentResult component = components.get(i);
            json.append("    {\n");
            json.append("      \"group\": \"").append(escape(component.getGroup())).append("\",\n");
            json.append("      \"name\": \"").append(escape(component.getName())).append("\",\n");
            json.append("      \"version\": \"").append(escape(component.getVersion())).append("\",\n");
            json.append("      \"licenses\": ");
            if (component.getLicenses().isEmpty()) {
                json.append("[\"").append(LicenseCollector.UNKNOWN).append("\"]");
            } else {
                json.append('[');
                for (int j = 0; j < component.getLicenses().size(); j++) {
                    LicenseInfo license = component.getLicenses().get(j);
                    if (j > 0) {
                        json.append(", ");
                    }
                    String name = license.getName().isEmpty() ? LicenseCollector.UNKNOWN : license.getName();
                    json.append("{\"name\": \"").append(escape(name)).append("\", \"url\": \"")
                            .append(escape(license.getUrl())).append("\"}");
                }
                json.append(']');
            }
            json.append(",\n");
            json.append("      \"verdict\": \"").append(component.getVerdict().name()).append("\",\n");
            String rule = component.getMatchedRule();
            json.append("      \"matchedRule\": ")
                    .append(rule == null ? "null" : "\"" + escape(rule) + "\"").append('\n');
            json.append("    }");
            if (i < components.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");

        Files.createDirectories(output.getParent());
        Files.write(output, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}

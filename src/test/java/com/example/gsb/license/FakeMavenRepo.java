package com.example.gsb.license;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a local Maven 2 repository on disk with jar + pom for each test
 * artifact, so TestKit builds never need network access.
 */
public final class FakeMavenRepo {

    private final Path root;

    public FakeMavenRepo(Path root) {
        this.root = root;
    }

    public Path getRoot() {
        return root;
    }

    /** Maven coordinate of the form group:name:version. */
    public record Coords(String group, String name, String version) {
        public String notation() {
            return group + ":" + name + ":" + version;
        }
    }

    public static Coords coords(String notation) {
        String[] parts = notation.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected group:name:version but got " + notation);
        }
        return new Coords(parts[0], parts[1], parts[2]);
    }

    /**
     * Publishes an artifact.
     *
     * @param licenseNames license {@code <name>} entries; empty means no
     *                     {@code <licenses>} section at all (unknown case)
     * @param deps         runtime dependencies on other published coordinates
     */
    public Path publish(Coords coords, List<String> licenseNames, List<Coords> deps) {
        Path moduleDir = root.resolve(coords.group().replace('.', '/'))
                .resolve(coords.name()).resolve(coords.version());
        try {
            Files.createDirectories(moduleDir);

            Path jar = moduleDir.resolve(coords.name() + "-" + coords.version() + ".jar");
            writeJar(jar);

            Path pom = moduleDir.resolve(coords.name() + "-" + coords.version() + ".pom");
            Files.write(pom, buildPom(coords, licenseNames, deps).getBytes(StandardCharsets.UTF_8));
            return jar;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Publishes a POM-only artifact (no jar), e.g. a BOM-style component. */
    public void publishPomOnly(Coords coords, List<String> licenseNames, List<Coords> deps) {
        Path moduleDir = root.resolve(coords.group().replace('.', '/'))
                .resolve(coords.name()).resolve(coords.version());
        try {
            Files.createDirectories(moduleDir);
            Path pom = moduleDir.resolve(coords.name() + "-" + coords.version() + ".pom");
            Files.write(pom, buildPom(coords, licenseNames, deps).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String buildPom(Coords coords, List<String> licenseNames, List<Coords> deps) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        builder.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        builder.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        builder.append("                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        builder.append("  <modelVersion>4.0.0</modelVersion>\n");
        builder.append("  <groupId>").append(coords.group()).append("</groupId>\n");
        builder.append("  <artifactId>").append(coords.name()).append("</artifactId>\n");
        builder.append("  <version>").append(coords.version()).append("</version>\n");
        if (!licenseNames.isEmpty()) {
            builder.append("  <licenses>\n");
            for (String licenseName : licenseNames) {
                builder.append("    <license>\n");
                builder.append("      <name>").append(licenseName).append("</name>\n");
                builder.append("      <url>https://example.test/licenses/")
                        .append(Integer.toHexString(licenseName.hashCode())).append("</url>\n");
                builder.append("    </license>\n");
            }
            builder.append("  </licenses>\n");
        }
        if (!deps.isEmpty()) {
            builder.append("  <dependencies>\n");
            for (Coords dep : deps) {
                builder.append("    <dependency>\n");
                builder.append("      <groupId>").append(dep.group()).append("</groupId>\n");
                builder.append("      <artifactId>").append(dep.name()).append("</artifactId>\n");
                builder.append("      <version>").append(dep.version()).append("</version>\n");
                builder.append("    </dependency>\n");
            }
            builder.append("  </dependencies>\n");
        }
        builder.append("</project>\n");
        return builder.toString();
    }

    private void writeJar(Path jar) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
            out.putNextEntry(entry);
            out.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}

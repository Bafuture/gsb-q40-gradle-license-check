package com.example.gsb.licensecheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Builds a tiny on-disk Maven 2 repository with POMs and empty JARs.
 * No network access is required at test time.
 */
final class FakeMavenRepo {

    record Module(String group, String name, String version,
                  List<String> licenses, List<String> dependencies) {
    }

    private final Path repoRoot;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    FakeMavenRepo(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    FakeMavenRepo module(String group, String name, String version, String... licenses) {
        modules.put(key(group, name, version),
                new Module(group, name, version, List.of(licenses), List.of()));
        return this;
    }

    FakeMavenRepo moduleWithDependencies(String group, String name, String version,
                                         List<String> licenses, List<String> dependencies) {
        modules.put(key(group, name, version),
                new Module(group, name, version, List.copyOf(licenses), List.copyOf(dependencies)));
        return this;
    }

    void build() {
        try {
            for (Module module : modules.values()) {
                Path dir = repoRoot
                        .resolve(module.group().replace('.', '/'))
                        .resolve(module.name())
                        .resolve(module.version());
                Files.createDirectories(dir);
                writePom(dir.resolve(module.name() + '-' + module.version() + ".pom"), module);
                writeEmptyJar(dir.resolve(module.name() + '-' + module.version() + ".jar"));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writePom(Path path, Module module) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
        builder.append("  <modelVersion>4.0.0</modelVersion>\n");
        builder.append("  <groupId>").append(module.group()).append("</groupId>\n");
        builder.append("  <artifactId>").append(module.name()).append("</artifactId>\n");
        builder.append("  <version>").append(module.version()).append("</version>\n");
        if (!module.licenses().isEmpty()) {
            builder.append("  <licenses>\n");
            for (String license : module.licenses()) {
                builder.append("    <license><name>").append(license)
                        .append("</name></license>\n");
            }
            builder.append("  </licenses>\n");
        }
        if (!module.dependencies().isEmpty()) {
            builder.append("  <dependencies>\n");
            for (String dependency : module.dependencies()) {
                String[] parts = dependency.split(":");
                builder.append("    <dependency>\n");
                builder.append("      <groupId>").append(parts[0]).append("</groupId>\n");
                builder.append("      <artifactId>").append(parts[1]).append("</artifactId>\n");
                builder.append("      <version>").append(parts[2]).append("</version>\n");
                builder.append("    </dependency>\n");
            }
            builder.append("  </dependencies>\n");
        }
        builder.append("</project>\n");
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private void writeEmptyJar(Path path) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");
            jar.putNextEntry(entry);
            jar.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    static String key(String group, String name, String version) {
        return group + ':' + name + ':' + version;
    }
}

package com.example.gsb.license;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resolves runtime components (including transitive dependencies) and reads
 * each component's licenses from its Maven POM metadata.
 *
 * <p>POM files are obtained from the resolved dependency metadata (the Gradle
 * module cache); a component whose POM is missing or declares no license is
 * kept with an empty license list so the report can mark it UNKNOWN instead of
 * silently dropping it.
 */
public class LicenseCollector {

    public static final String UNKNOWN = "UNKNOWN";

    private static final Attribute<String> ARTIFACT_TYPE =
            Attribute.of("artifactType", String.class);

    private final PomParser pomParser;

    public LicenseCollector(PomParser pomParser) {
        this.pomParser = pomParser;
    }

    /**
     * Resolves all external module components found on the given runtime
     * configuration (typically {@code runtimeClasspath}).
     */
    public List<ResolvedComponent> collect(Configuration runtimeConfiguration) {
        // component key -> one representative artifact file (usually the jar)
        Map<String, ModuleComponentIdentifier> components = new TreeMap<>();
        Map<String, File> artifactFiles = new TreeMap<>();

        Map<ModuleComponentIdentifier, File> pomFiles = resolvePomFiles(runtimeConfiguration);

        for (ResolvedArtifactResult artifact : resolveArtifacts(runtimeConfiguration, "jar")) {
            ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
            if (!(componentId instanceof ModuleComponentIdentifier)) {
                continue; // project dependencies and local files are out of scope
            }
            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) componentId;
            String key = key(moduleId);
            components.putIfAbsent(key, moduleId);
            artifactFiles.putIfAbsent(key, artifact.getFile());
        }

        List<ResolvedComponent> result = new ArrayList<>();
        for (Map.Entry<String, ModuleComponentIdentifier> entry : components.entrySet()) {
            ModuleComponentIdentifier moduleId = entry.getValue();
            File artifactFile = artifactFiles.get(entry.getKey());
            File pom = pomFiles.get(moduleId);
            if (pom == null) {
                // file:// Maven repositories keep the pom next to the jar
                pom = findPomNextToArtifact(moduleId, artifactFile);
            }
            if (pom == null) {
                pom = findPomInCache(moduleId, artifactFile);
            }
            if (pom == null) {
                pom = findPomInLocalMavenRepo(moduleId);
            }
            List<LicenseInfo> licenses = pomParser.parseLicenses(pom);
            result.add(new ResolvedComponent(
                    moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion(), licenses));
        }
        result.sort(Comparator.comparing(ResolvedComponent::getGroup)
                .thenComparing(ResolvedComponent::getName)
                .thenComparing(ResolvedComponent::getVersion));
        return result;
    }

    private List<ResolvedArtifactResult> resolveArtifacts(Configuration configuration, String type) {
        List<ResolvedArtifactResult> resolved = new ArrayList<>();
        try {
            ArtifactCollection artifacts = configuration.getIncoming()
                    .artifactView(view -> {
                        view.lenient(true);
                        view.attributes(attributes -> attributes.attribute(ARTIFACT_TYPE, type));
                    })
                    .getArtifacts();
            for (ResolvedArtifactResult result : artifacts.getArtifacts()) {
                resolved.add(result);
            }
        } catch (Exception e) {
            // Lenient resolution failure for one view degrades to empty metadata;
            // components themselves are still reported as UNKNOWN.
        }
        return resolved;
    }

    private Map<ModuleComponentIdentifier, File> resolvePomFiles(Configuration configuration) {
        Map<ModuleComponentIdentifier, File> pomFiles = new java.util.HashMap<>();
        for (ResolvedArtifactResult artifact : resolveArtifacts(configuration, "pom")) {
            ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
            if (componentId instanceof ModuleComponentIdentifier) {
                pomFiles.put((ModuleComponentIdentifier) componentId, artifact.getFile());
            }
        }
        return pomFiles;
    }

    /**
     * Layout used by local file-based Maven repositories: the POM is a
     * sibling of the resolved jar in the same version directory.
     */
    private File findPomNextToArtifact(ModuleComponentIdentifier moduleId, File artifactFile) {
        if (artifactFile == null || artifactFile.getParentFile() == null) {
            return null;
        }
        File expected = new File(artifactFile.getParentFile(),
                moduleId.getModule() + "-" + moduleId.getVersion() + ".pom");
        return expected.isFile() ? expected : null;
    }

    /**
     * Cache-layout fallback: the Gradle module cache stores every downloaded
     * artifact under
     * {@code caches/modules-2/files-2.1/<group>/<name>/<version>/<sha1>/}.
     * Given the component's jar we can scan the version directory for its pom.
     */
    private File findPomInCache(ModuleComponentIdentifier moduleId, File artifactFile) {
        if (artifactFile == null) {
            return null;
        }
        File hashDir = artifactFile.getParentFile();
        if (hashDir == null) {
            return null;
        }
        File versionDir = hashDir.getParentFile();
        if (versionDir == null || !versionDir.getName().equals(moduleId.getVersion())) {
            return null;
        }
        File[] hashDirs = versionDir.listFiles(File::isDirectory);
        if (hashDirs == null) {
            return null;
        }
        File expected = new File(moduleId.getModule() + "-" + moduleId.getVersion() + ".pom");
        for (File dir : hashDirs) {
            File[] poms = dir.listFiles((d, fileName) -> fileName.endsWith(".pom"));
            if (poms == null) {
                continue;
            }
            for (File pom : poms) {
                if (pom.getName().equals(expected.getName())) {
                    return pom;
                }
            }
            if (poms.length > 0) {
                return poms[0];
            }
        }
        return null;
    }

    /** Last-resort lookup in a local {@code ~/.m2/repository} layout. */
    private File findPomInLocalMavenRepo(ModuleComponentIdentifier moduleId) {
        File repository = new File(System.getProperty("user.home"), ".m2/repository");
        File moduleDir = new File(repository,
                moduleId.getGroup().replace('.', '/') + "/" + moduleId.getModule() + "/"
                        + moduleId.getVersion());
        File pom = new File(moduleDir, moduleId.getModule() + "-" + moduleId.getVersion() + ".pom");
        return pom.isFile() ? pom : null;
    }

    /**
     * Stable fingerprint of the resolved dependency set and its license
     * metadata; used as a task input so dependency/metadata changes invalidate
     * the up-to-date check.
     */
    public static String fingerprint(List<ResolvedComponent> components) {
        List<String> lines = new ArrayList<>();
        for (ResolvedComponent component : components) {
            List<String> names = new ArrayList<>();
            for (LicenseInfo license : component.getLicenses()) {
                String label = license.getName();
                if (label != null && !label.isEmpty()) {
                    names.add(label);
                }
            }
            names.sort(String::compareTo);
            String licensePart = names.isEmpty() ? UNKNOWN : String.join(",", names);
            lines.add(component.getGroup() + ":" + component.getName() + ":" + component.getVersion()
                    + "|" + licensePart);
        }
        lines.sort(String::compareTo);
        return String.join("\n", lines);
    }

    private static String key(ModuleComponentIdentifier id) {
        return id.getGroup() + ":" + id.getModule() + ":" + id.getVersion();
    }
}

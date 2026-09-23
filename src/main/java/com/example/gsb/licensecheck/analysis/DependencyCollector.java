package com.example.gsb.licensecheck.analysis;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the runtime classpath and the corresponding POM files.
 * POMs are fetched as detached &quot;group:name:version@pom&quot; dependencies so they can be
 * declared as task inputs without resolving the configuration during the build script phase.
 */
public class DependencyCollector {

    private final Project project;
    private final String configurationName;

    private Set<ResolvedModule> modulesCache;
    private Map<String, File> pomsCache;

    public DependencyCollector(Project project, String configurationName) {
        this.project = project;
        this.configurationName = configurationName;
    }

    public Set<ResolvedModule> collectModules() {
        if (modulesCache == null) {
            modulesCache = doCollectModules();
        }
        return modulesCache;
    }

    public Map<String, File> collectPomFiles() {
        if (pomsCache == null) {
            pomsCache = doCollectPomFiles(collectModules());
        }
        return pomsCache;
    }

    private Set<ResolvedModule> doCollectModules() {
        Configuration runtime = project.getConfigurations().getByName(configurationName);
        ResolutionResult result = runtime.getIncoming().getResolutionResult();
        ResolvedComponentResult root = result.getRoot();
        Set<ResolvedModule> modules = new LinkedHashSet<>();
        collectModules(root, modules, new LinkedHashSet<>());

        if (modules.isEmpty()) {
            for (ResolvedDependency dependency : runtime.getResolvedConfiguration()
                    .getLenientConfiguration().getFirstLevelModuleDependencies()) {
                legacyCollect(dependency, modules, new LinkedHashSet<>());
            }
        }
        return modules;
    }

    private void collectModules(ResolvedComponentResult component,
                                Set<ResolvedModule> collected,
                                Set<String> visited) {
        for (org.gradle.api.artifacts.result.DependencyResult dependency : component.getDependencies()) {
            if (!(dependency instanceof ResolvedDependencyResult resolved)) {
                continue;
            }
            ResolvedComponentResult selected = resolved.getSelected();
            if (!(selected.getId() instanceof ModuleComponentIdentifier id)) {
                continue;
            }
            String key = id.getGroup() + ':' + id.getModule() + ':' + id.getVersion();
            if (visited.add(key)) {
                collected.add(new ResolvedModule(id.getGroup(), id.getModule(), id.getVersion()));
                collectModules(selected, collected, visited);
            }
        }
    }

    private void legacyCollect(ResolvedDependency dependency,
                               Set<ResolvedModule> collected,
                               Set<String> visited) {
        if (dependency.getModule().getId() instanceof ModuleComponentIdentifier moduleId
                && visited.add(moduleId.getGroup() + ':' + moduleId.getModule() + ':' + moduleId.getVersion())) {
            collected.add(new ResolvedModule(
                    moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion()));
            for (ResolvedDependency child : dependency.getChildren()) {
                legacyCollect(child, collected, visited);
            }
        }
    }

    private Map<String, File> doCollectPomFiles(Set<ResolvedModule> modules) {
        Map<String, File> poms = new LinkedHashMap<>();
        if (modules.isEmpty()) {
            return poms;
        }
        Configuration detached = project.getConfigurations().detachedConfiguration();
        detached.setTransitive(false);
        for (ResolvedModule module : modules) {
            String notation = module.group() + ':' + module.name() + ':' + module.version() + "@pom";
            detached.getDependencies().add(project.getDependencies().create(notation));
        }
        Set<File> files = detached.getResolvedConfiguration()
                .getLenientConfiguration().getFiles();
        Map<String, File> byName = new LinkedHashMap<>();
        for (File file : files) {
            byName.put(file.getName(), file);
        }
        for (ResolvedModule module : modules) {
            String expectedPomName = module.name() + '-' + module.version() + ".pom";
            File pom = byName.get(expectedPomName);
            if (pom == null) {
                for (Map.Entry<String, File> entry : byName.entrySet()) {
                    if (entry.getKey().startsWith(module.name() + '-') && entry.getKey().endsWith(".pom")) {
                        pom = entry.getValue();
                        break;
                    }
                }
            }
            if (pom != null) {
                poms.put(module.coordinates(), pom);
            }
        }
        return poms;
    }
}

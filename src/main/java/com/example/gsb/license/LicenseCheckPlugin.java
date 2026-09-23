package com.example.gsb.license;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;

import java.util.List;

/**
 * Entry point of the {@code com.example.gsb.license-check} plugin.
 */
public class LicenseCheckPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "licenseCheck";
    public static final String TASK_NAME = "checkLicenses";

    @Override
    public void apply(Project project) {
        LicenseCheckExtension extension =
                project.getExtensions().create(EXTENSION_NAME, LicenseCheckExtension.class);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> registerTask(project, extension));
    }

    private void registerTask(Project project, LicenseCheckExtension extension) {
        Configuration runtimeClasspath = project.getConfigurations()
                .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        project.getTasks().register(TASK_NAME, CheckLicensesTask.class, task -> {
            task.getRuntimeClasspath().from(runtimeClasspath);

            task.getTextReport().convention(project.getLayout().getBuildDirectory()
                    .file("reports/license-check/license-report.txt"));
            task.getJsonReport().convention(project.getLayout().getBuildDirectory()
                    .file("reports/license-check/license-report.json"));

            // Rule snapshot and dependency fingerprint are computed lazily at
            // input-snapshot time, after the build script DSL has been evaluated.
            task.getRules().convention(project.provider(() -> {
                // Force realization of lazily-registered coordinate rules so
                // their configure actions run before taking the snapshot.
                extension.getRules().stream().count();
                return new RulesSnapshot(
                        extension.getAllowLicenses(),
                        extension.getDenyLicenses(),
                        extension.getAllowUnknown(),
                        extension.getFailOnError(),
                        extension.getRules().getAsMap());
            }));

            task.getDependencyFingerprint().convention(project.provider(() -> {
                LicenseCollector collector = new LicenseCollector(new PomParser());
                List<ResolvedComponent> components = collector.collect(runtimeClasspath);
                return LicenseCollector.fingerprint(components);
            }));

        });
        project.getTasks().named(org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(
                        project.getTasks().named(TASK_NAME)));
    }
}

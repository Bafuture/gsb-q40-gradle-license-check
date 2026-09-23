package com.example.gsb.licensecheck;

import com.example.gsb.licensecheck.analysis.DependencyCollector;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;

import java.io.File;

import java.util.ArrayList;

public class LicenseCheckPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "licenseCheck";
    public static final String TASK_NAME = "licenseCheck";
    private static final String RUNTIME_CONFIGURATION = "runtimeClasspath";

    @Override
    public void apply(Project project) {
        LicenseCheckExtension extension = project.getExtensions().create(
                EXTENSION_NAME, LicenseCheckExtension.class);
        extension.getReportsDir().convention(
                project.getLayout().getBuildDirectory().dir("reports/license-check"));

        project.getPluginManager().withPlugin("java", plugin -> configureJavaProject(project, extension));
    }

    private void configureJavaProject(Project project, LicenseCheckExtension extension) {
        DependencyCollector collector = new DependencyCollector(project, RUNTIME_CONFIGURATION);

        project.getTasks().register(TASK_NAME, LicenseCheckTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Checks third-party runtime dependency licenses against the configured "
                    + "allow/deny rules and writes text and JSON reports.");
            task.getLenient().set(extension.getLenient());
            task.getAllowedLicenses().set(extension.getAllowedLicenses());
            task.getDeniedLicenses().set(extension.getDeniedLicenses());
            task.getAllowCoordinates().set(extension.getAllowCoordinates());
            task.getDenyCoordinates().set(extension.getDenyCoordinates());
            task.getTextReportEnabled().set(extension.getTextReport());
            task.getJsonReportEnabled().set(extension.getJsonReport());
            task.setCollector(collector);
            task.getRuntimeClasspath().from(
                    project.getConfigurations().getByName(RUNTIME_CONFIGURATION));
            task.getPomMetadataFiles().from(project.provider(() -> {
                try {
                    return new ArrayList<>(collector.collectPomFiles().values());
                } catch (Exception ignored) {
                    return new ArrayList<File>();
                }
            }));
            task.getReportsDirectory().set(extension.getReportsDir());
        });

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(TASK_NAME));
    }
}

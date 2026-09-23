package com.example.gsb.license;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.NormalizeLineEndings;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects runtime dependency licenses, evaluates them against the configured
 * rules, writes text/JSON reports and fails the build (or warns) on violations.
 */
public abstract class CheckLicensesTask extends DefaultTask {

    /**
     * The resolved runtime classpath. Declared as a (path-insensitive) input so
     * a changed dependency set invalidates the up-to-date state.
     */
    @InputFiles
    @NormalizeLineEndings
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /** Fingerprint of the dependency graph plus per-component license metadata. */
    @Input
    public abstract Property<String> getDependencyFingerprint();

    /** Serialized snapshot of the DSL rule configuration. */
    @Input
    public abstract Property<RulesSnapshot> getRules();

    @OutputFile
    public abstract RegularFileProperty getTextReport();

    @OutputFile
    public abstract RegularFileProperty getJsonReport();

    @Inject
    public CheckLicensesTask() {
        setGroup("verification");
        setDescription("Checks third-party dependency licenses against the configured allow/deny rules.");
    }

    @TaskAction
    public void check() {
        RulesSnapshot rulesSnapshot = getRules().get();
        LicenseCollector collector = new LicenseCollector(new PomParser());
        org.gradle.api.artifacts.Configuration configuration = getProject().getConfigurations()
                .getByName("runtimeClasspath");

        List<ResolvedComponent> components = collector.collect(configuration);
        RuleEngine engine = new RuleEngine();
        List<ComponentResult> results = new ArrayList<>();
        for (ResolvedComponent component : components) {
            results.add(engine.evaluate(component, rulesSnapshot));
        }

        List<ComponentResult> denied = new ArrayList<>();
        List<ComponentResult> unknown = new ArrayList<>();
        for (ComponentResult result : results) {
            if (result.getVerdict() == Verdict.DENIED) {
                denied.add(result);
            } else if (result.getVerdict() == Verdict.UNKNOWN) {
                unknown.add(result);
            }
        }

        try {
            new TextReportWriter().write(getTextReport().get().getAsFile().toPath(),
                    results, rulesSnapshot.isFailOnError());
            new JsonReportWriter().write(getJsonReport().get().getAsFile().toPath(),
                    results, rulesSnapshot.isFailOnError());
        } catch (Exception e) {
            throw new GradleException("Failed to write license reports: " + e.getMessage(), e);
        }

        for (ComponentResult result : denied) {
            getLogger().warn("[license-check] BLACKLISTED license: {} [{}]",
                    result.getCoordinates(), result.getMatchedRule());
        }
        for (ComponentResult result : unknown) {
            getLogger().warn("[license-check] UNKNOWN license (not explicitly allowed): {}",
                    result.getCoordinates());
        }

        if (!denied.isEmpty() || !unknown.isEmpty()) {
            String summary = denied.size() + " blacklisted component(s) and "
                    + unknown.size() + " component(s) with unknown, disallowed license(s)";
            if (rulesSnapshot.isFailOnError()) {
                throw new GradleException("License check failed: " + summary
                        + ". See " + getTextReport().get().getAsFile()
                        + " or relax the gate with 'failOnError = false' after legal review.");
            }
            getLogger().warn("[license-check] WARNING - license violations found but failOnError=false: {}",
                    summary);
        } else {
            getLogger().lifecycle("[license-check] All {} runtime components passed license rules.",
                    results.size());
        }
    }
}

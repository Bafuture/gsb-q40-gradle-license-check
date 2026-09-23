package com.example.gsb.licensecheck;

import com.example.gsb.licensecheck.analysis.DependencyCollector;
import com.example.gsb.licensecheck.analysis.PomLicenseParser;
import com.example.gsb.licensecheck.analysis.ResolvedModule;
import com.example.gsb.licensecheck.model.ComponentReport;
import com.example.gsb.licensecheck.model.ComplianceResult;
import com.example.gsb.licensecheck.model.DeclaredLicense;
import com.example.gsb.licensecheck.model.Verdict;
import com.example.gsb.licensecheck.report.JsonReportWriter;
import com.example.gsb.licensecheck.report.TextReportWriter;
import com.example.gsb.licensecheck.rules.RuleEvaluation;
import com.example.gsb.licensecheck.rules.RuleEvaluator;
import com.example.gsb.licensecheck.rules.RuleSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public abstract class LicenseCheckTask extends DefaultTask {

    @Input
    public abstract Property<Boolean> getLenient();

    @Input
    public abstract ListProperty<String> getAllowedLicenses();

    @Input
    public abstract ListProperty<String> getDeniedLicenses();

    @Input
    public abstract MapProperty<String, String> getAllowCoordinates();

    @Input
    public abstract MapProperty<String, String> getDenyCoordinates();

    @Input
    public abstract Property<Boolean> getTextReportEnabled();

    @Input
    public abstract Property<Boolean> getJsonReportEnabled();

    @InputFiles
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @InputFiles
    @org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getPomMetadataFiles();

    @OutputDirectory
    public abstract DirectoryProperty getReportsDirectory();

    @Internal
    DependencyCollector getCollector() {
        return collector;
    }

    void setCollector(DependencyCollector collector) {
        this.collector = collector;
    }

    private DependencyCollector collector;

    @TaskAction
    public void checkLicenses() throws Exception {
        RuleSet ruleSet = new RuleSet(
                getAllowedLicenses().get(),
                getDeniedLicenses().get(),
                getAllowCoordinates().get(),
                getDenyCoordinates().get());
        RuleEvaluator evaluator = new RuleEvaluator(ruleSet);

        Map<String, File> poms = collector.collectPomFiles();

        List<ComponentReport> components = new ArrayList<>();
        for (ResolvedModule module : new ArrayList<>(collector.collectModules())) {
            File pomFile = poms.get(module.coordinates());
            List<DeclaredLicense> licenses;
            String source;
            if (pomFile != null) {
                licenses = PomLicenseParser.parse(pomFile);
                source = licenses.isEmpty()
                        ? "pom metadata (no <licenses> entry): " + relativePomPath(pomFile)
                        : "pom metadata: " + relativePomPath(pomFile);
            } else {
                licenses = List.of();
                source = "unresolved pom metadata";
            }
            RuleEvaluation evaluation = evaluator.evaluate(module, licenses);
            components.add(new ComponentReport(
                    module.group(),
                    module.name(),
                    module.version(),
                    licenses,
                    evaluation.getVerdict(),
                    evaluation.getMatchedRule(),
                    source));
        }
        components.sort(Comparator.comparing(ComponentReport::getCoordinates));

        List<String> violations = new ArrayList<>();
        for (ComponentReport component : components) {
            if (component.getVerdict() == Verdict.DENIED) {
                violations.add("DENIED  " + component.getCoordinates()
                        + " - " + component.getMatchedRule());
            } else if (component.getVerdict() == Verdict.UNKNOWN) {
                violations.add("UNKNOWN " + component.getCoordinates()
                        + " - " + component.getMatchedRule());
            }
        }

        ComplianceResult result = new ComplianceResult(components, violations);
        File reportDir = getReportsDirectory().get().getAsFile();
        File textReport = new File(reportDir, "license-report.txt");
        File jsonReport = new File(reportDir, "license-report.json");
        if (getTextReportEnabled().get()) {
            TextReportWriter.write(textReport.toPath(), result, getLenient().get());
        }
        if (getJsonReportEnabled().get()) {
            JsonReportWriter.write(jsonReport.toPath(), result, getLenient().get());
        }

        getLogger().lifecycle("License report written to {} (text={}, json={})",
                reportDir, getTextReportEnabled().get(), getJsonReportEnabled().get());
        for (String violation : violations) {
            getLogger().warn(violation);
        }

        if (result.hasViolations()) {
            String message = "License policy violated by " + violations.size() + " component(s). "
                    + "See " + textReport;
            if (getLenient().get()) {
                getLogger().warn("[license-check lenient] " + message);
            } else {
                throw new GradleException(message);
            }
        }
    }

    private String relativePomPath(File pomFile) {
        try {
            return getProject().getRootDir().toPath().toAbsolutePath()
                    .relativize(pomFile.toPath().toAbsolutePath()).toString();
        } catch (IllegalArgumentException ignored) {
            return pomFile.getAbsolutePath();
        }
    }
}

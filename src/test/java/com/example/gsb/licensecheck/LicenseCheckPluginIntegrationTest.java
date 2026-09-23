package com.example.gsb.licensecheck;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseCheckPluginIntegrationTest {

    @TempDir
    Path projectDir;

    private Path repoDir;

    private void scaffoldProject() throws IOException {
        repoDir = projectDir.resolve("fake-repo");
        FakeMavenRepo repo = new FakeMavenRepo(repoDir);
        repo.module("com.example.libs", "guava-like", "1.0", "Apache License, Version 2.0")
                .module("com.example.libs", "slf4j-like", "2.0", "MIT License")
                .moduleWithDependencies("com.example.app", "app", "3.1",
                        List.of("Apache License, Version 2.0"),
                        List.of("com.example.libs:guava-like:1.0",
                                "com.example.libs:slf4j-like:2.0"))
                .module("com.example.libs", "gpl-lib", "9.9",
                        "GNU General Public License, Version 3")
                .module("com.example.libs", "no-license-lib", "4.2")
                .build();
    }

    private void writeBuildFile(String licenseCheckDsl) throws IOException {
        String buildFile = """
                plugins {
                    id 'java'
                    id 'com.example.gsb.license-check'
                }

                repositories {
                    maven { url = uri(%s) }
                }

                dependencies {
                    implementation 'com.example.app:app:3.1'
                }

                %s
                """.formatted(quotedRepoUri(), licenseCheckDsl);
        Files.writeString(projectDir.resolve("build.gradle"), buildFile, StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'license-check-it'\n", StandardCharsets.UTF_8);
    }

    private String quotedRepoUri() {
        String uri = repoDir.toUri().toString().replace("'", "\\'");
        return "'" + uri + "'";
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(withDefaults(arguments))
                .withPluginClasspath()
                .forwardOutput();
    }

    private String[] withDefaults(String[] arguments) {
        List<String> all = new java.util.ArrayList<>(List.of(arguments));
        all.add("--offline");
        return all.toArray(String[]::new);
    }

    private String textReport() throws IOException {
        return Files.readString(
                projectDir.resolve("build/reports/license-check/license-report.txt"),
                StandardCharsets.UTF_8);
    }

    private String jsonReport() throws IOException {
        return Files.readString(
                projectDir.resolve("build/reports/license-check/license-report.json"),
                StandardCharsets.UTF_8);
    }

    @Test
    void passesWhenAllLicensesWhitelisted() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                }
                """);

        BuildResult result = runner("licenseCheck").build();
        assertEquals(SUCCESS, result.task(":licenseCheck").getOutcome());
        String text = textReport();
        assertTrue(text.contains("com.example.libs:guava-like:1.0"));
        assertTrue(text.contains("com.example.libs:slf4j-like:2.0"));
        assertTrue(text.contains("com.example.app:app:3.1"));
        assertTrue(text.contains("Verdict   : allowed"));
        assertTrue(text.contains("Result: PASS"));

        String json = jsonReport();
        assertTrue(json.contains("\"status\": \"pass\""));
        assertTrue(json.contains("\"verdict\": \"allowed\""));
    }

    @Test
    void failsWhenBlacklistedLicenseIsDeclared() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                    denyLicense 'GNU General Public License, Version 3'
                }

                dependencies {
                    implementation 'com.example.libs:gpl-lib:9.9'
                }
                """);

        BuildResult result = runner("licenseCheck").buildAndFail();
        assertEquals(FAILED, result.task(":licenseCheck").getOutcome());
        String text = textReport();
        assertTrue(text.contains("com.example.libs:gpl-lib:9.9"));
        assertTrue(text.contains("Verdict   : denied"));
        assertTrue(text.contains("license blacklist: 'GNU General Public License, Version 3'"));
        assertTrue(text.contains("Result: FAIL"));
        String json = jsonReport();
        assertTrue(json.contains("\"status\": \"fail\""));
        assertTrue(json.contains("\"verdict\": \"denied\""));
    }

    @Test
    void warnsAndSucceedsForUnknownLicenseWhenLenient() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                    lenient = true
                }

                dependencies {
                    implementation 'com.example.libs:no-license-lib:4.2'
                }
                """);

        BuildResult result = runner("licenseCheck").build();
        assertEquals(SUCCESS, result.task(":licenseCheck").getOutcome());
        String text = textReport();
        assertTrue(text.contains("com.example.libs:no-license-lib:4.2"));
        assertTrue(text.contains("Licenses  : UNKNOWN"));
        assertTrue(text.contains("Verdict   : unknown"));
        assertTrue(text.contains("Result: WARN"));
        String json = jsonReport();
        assertTrue(json.contains("\"status\": \"warn\""));
        assertTrue(json.contains("\"verdict\": \"unknown\""));
    }

    @Test
    void failsByDefaultForUnknownLicense() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                }

                dependencies {
                    implementation 'com.example.libs:no-license-lib:4.2'
                }
                """);

        BuildResult result = runner("licenseCheck").buildAndFail();
        assertEquals(FAILED, result.task(":licenseCheck").getOutcome());
        assertTrue(textReport().contains("Verdict   : unknown"));
    }

    @Test
    void coordinateAllowRuleOverridesUnknownLicense() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                    allowCoordinate 'com.example.libs:no-license-lib:*',
                            'legal ticket 42: reviewed and accepted'
                }

                dependencies {
                    implementation 'com.example.libs:no-license-lib:4.2'
                }
                """);

        BuildResult result = runner("licenseCheck").build();
        assertEquals(SUCCESS, result.task(":licenseCheck").getOutcome());
        String text = textReport();
        assertTrue(text.contains("Verdict   : allowed"));
        assertTrue(text.contains("coordinate allow-rule 'com.example.libs:no-license-lib:*'"));
        assertTrue(text.contains("legal ticket 42"));
    }

    @Test
    void coordinateDenyRuleOverridesWhitelistedLicense() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                    denyCoordinate 'com.example.app:app:*', 'legacy vendor, embargo'
                }
                """);

        BuildResult result = runner("licenseCheck").buildAndFail();
        assertEquals(FAILED, result.task(":licenseCheck").getOutcome());
        String text = textReport();
        assertTrue(text.contains("Verdict   : denied"));
        assertTrue(text.contains("coordinate deny-rule 'com.example.app:app:*'"));
        assertTrue(text.contains("embargo"));
    }

    @Test
    void isUpToDateWhenInputsDoNotChange() throws IOException {
        scaffoldProject();
        writeBuildFile("""
                licenseCheck {
                    allowedLicenses = ['Apache License, Version 2.0', 'MIT License']
                }
                """);

        BuildResult first = runner("licenseCheck").build();
        assertEquals(SUCCESS, first.task(":licenseCheck").getOutcome());

        BuildResult second = runner("licenseCheck").build();
        assertNotNull(second.task(":licenseCheck"));
        TaskOutcome secondOutcome = second.task(":licenseCheck").getOutcome();
        assertEquals(UP_TO_DATE, secondOutcome);
    }
}

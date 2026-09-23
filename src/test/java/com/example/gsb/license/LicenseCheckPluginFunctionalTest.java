package com.example.gsb.license;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TestKit integration tests backed entirely by a local, on-disk fake Maven
 * repository; runs fully offline.
 */
class LicenseCheckPluginFunctionalTest {

    private static final String APACHE = "Apache License, Version 2.0";
    private static final String MIT = "MIT License";
    private static final String GPL3 = "GNU General Public License, version 3";

    @TempDir
    Path projectDir;
    @TempDir
    Path testUserHome;

    private Path repoDir;
    private FakeMavenRepo repo;

    @BeforeEach
    void setUp() throws IOException {
        repoDir = projectDir.resolve("fake-repo");
        repo = new FakeMavenRepo(repoDir);

        Files.createDirectories(projectDir.resolve("src/main/java/demo"));
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'demo-app'\n");
    }

    private String buildFileBase() {
        return "plugins {\n"
                + "    id 'java'\n"
                + "    id 'com.example.gsb.license-check'\n"
                + "}\n"
                + "repositories {\n"
                + "    maven { url = uri('" + repoDir.toString().replace("\\", "\\\\") + "') }\n"
                + "}\n";
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withTestKitDir(testUserHome.toFile())
                .withPluginClasspath()
                .withArguments(concat(arguments, "--offline", "--stacktrace"))
                .forwardOutput();
    }

    private static String[] concat(String[] first, String... rest) {
        String[] all = new String[first.length + rest.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(rest, 0, all, first.length, rest.length);
        return all;
    }

    private void writeBuildFile(String body) throws IOException {
        Files.writeString(projectDir.resolve("build.gradle"), buildFileBase() + body + "\n",
                StandardCharsets.UTF_8);
    }

    private String reportText() throws IOException {
        return Files.readString(projectDir.resolve("build/reports/license-check/license-report.txt"),
                StandardCharsets.UTF_8);
    }

    private String reportJson() throws IOException {
        return Files.readString(projectDir.resolve("build/reports/license-check/license-report.json"),
                StandardCharsets.UTF_8);
    }

    @Test
    void passesWhenAllLicensesAllowedIncludingTransitive() throws IOException {
        FakeMavenRepo.Coords lib = FakeMavenRepo.coords("com.example:app-lib:1.0.0");
        FakeMavenRepo.Coords transitive = FakeMavenRepo.coords("org.example:transitive:2.1.0");
        repo.publish(transitive, List.of(MIT), List.of());
        repo.publish(lib, List.of(APACHE), List.of(transitive));

        writeBuildFile(
                "dependencies { implementation 'com.example:app-lib:1.0.0' }\n"
                        + "licenseCheck {\n"
                        + "    allowLicenses = ['" + APACHE + "', '" + MIT + "']\n"
                        + "}\n");

        BuildResult result = runner("checkLicenses").build();
        assertEquals(SUCCESS, result.task(":checkLicenses").getOutcome());

        String text = reportText();
        assertTrue(text.contains("com.example:app-lib:1.0.0"), text);
        assertTrue(text.contains("org.example:transitive:2.1.0"), "transitive dep must be included");
        assertTrue(text.contains(APACHE), text);
        assertTrue(text.contains(MIT), text);
        assertTrue(text.contains("Verdict   : ALLOWED"), text);
        assertFalse(text.contains("Verdict   : UNKNOWN"), text);

        String json = reportJson();
        assertTrue(json.contains("\"verdict\": \"ALLOWED\""), json);
        assertTrue(json.contains("com.example"), json);

        // Re-running with unchanged inputs must be up-to-date.
        BuildResult second = runner("checkLicenses").build();
        assertEquals(UP_TO_DATE, second.task(":checkLicenses").getOutcome());
    }

    @Test
    void failsOnBlacklistedLicenseAndPassesAfterWarnOnly() throws IOException {
        FakeMavenRepo.Coords gplLib = FakeMavenRepo.coords("com.gpl:gpl-lib:3.0.0");
        FakeMavenRepo.Coords apacheLib = FakeMavenRepo.coords("com.apache:ok-lib:1.0.0");
        repo.publish(gplLib, List.of(GPL3), List.of());
        repo.publish(apacheLib, List.of(APACHE), List.of());

        writeBuildFile(
                "dependencies {\n"
                        + "    implementation 'com.gpl:gpl-lib:3.0.0'\n"
                        + "    implementation 'com.apache:ok-lib:1.0.0'\n"
                        + "}\n"
                        + "licenseCheck {\n"
                        + "    allowLicenses = ['" + APACHE + "']\n"
                        + "    denyLicenses  = ['" + GPL3 + "']\n"
                        + "}\n");

        BuildResult failed = runner("checkLicenses").buildAndFail();
        assertEquals(FAILED, failed.task(":checkLicenses").getOutcome());
        assertTrue(failed.getOutput().contains("License check failed"), failed.getOutput());
        assertTrue(reportText().contains("Verdict   : DENIED"));
        assertTrue(reportText().contains("global:deny:" + GPL3));
        assertTrue(reportJson().contains("\"verdict\": \"DENIED\""));
        // nonzero exit code is what GradleRunner.buildAndFail() asserts

        // Relaxing to warn-only must succeed while still flagging the violation.
        writeBuildFile(
                "dependencies {\n"
                        + "    implementation 'com.gpl:gpl-lib:3.0.0'\n"
                        + "    implementation 'com.apache:ok-lib:1.0.0'\n"
                        + "}\n"
                        + "licenseCheck {\n"
                        + "    allowLicenses = ['" + APACHE + "']\n"
                        + "    denyLicenses  = ['" + GPL3 + "']\n"
                        + "    failOnError = false\n"
                        + "}\n");
        BuildResult warned = runner("checkLicenses").build();
        assertEquals(SUCCESS, warned.task(":checkLicenses").getOutcome());
        assertTrue(warned.getOutput().contains("WARNING"), warned.getOutput());
        assertTrue(reportText().contains("PASSED WITH WARNINGS"), reportText());
    }

    @Test
    void unknownLicenseFailsByDefaultAndWarnsWhenRelaxed() throws IOException {
        FakeMavenRepo.Coords mystery = FakeMavenRepo.coords("com.mystery:no-license:9.9");
        FakeMavenRepo.Coords ok = FakeMavenRepo.coords("com.fine:licensed:1.0");
        repo.publish(mystery, List.of(), List.of()); // POM without <licenses>
        repo.publish(ok, List.of(APACHE), List.of());

        writeBuildFile(
                "dependencies {\n"
                        + "    implementation 'com.mystery:no-license:9.9'\n"
                        + "    implementation 'com.fine:licensed:1.0'\n"
                        + "}\n"
                        + "licenseCheck {\n"
                        + "    allowLicenses = ['" + APACHE + "']\n"
                        + "}\n");

        BuildResult failed = runner("checkLicenses").buildAndFail();
        assertEquals(FAILED, failed.task(":checkLicenses").getOutcome());
        String text = reportText();
        assertTrue(text.contains("com.mystery:no-license:9.9"), text);
        assertTrue(text.contains("UNKNOWN"), "unknown components must be listed, not skipped");
        assertTrue(text.contains("Verdict   : UNKNOWN"), text);
        assertTrue(reportJson().contains("\"verdict\": \"UNKNOWN\""));
        assertTrue(reportJson().contains("\"UNKNOWN\""), "JSON licenses must carry the UNKNOWN marker");

        // warn-only mode: build succeeds and the report still flags the component
        writeBuildFile(
                "dependencies {\n"
                        + "    implementation 'com.mystery:no-license:9.9'\n"
                        + "    implementation 'com.fine:licensed:1.0'\n"
                        + "}\n"
                        + "licenseCheck {\n"
                        + "    allowLicenses = ['" + APACHE + "']\n"
                        + "    failOnError = false\n"
                        + "}\n");
        BuildResult warned = runner("checkLicenses").build();
        assertEquals(SUCCESS, warned.task(":checkLicenses").getOutcome());
        assertTrue(warned.getOutput().contains("UNKNOWN license"), warned.getOutput());
        assertTrue(reportText().contains("Verdict   : UNKNOWN"));

        // Explicit global allowUnknown turns the verdict into ALLOWED
        writeBuildFile(
                "dependencies { implementation 'com.mystery:no-license:9.9' }\n"
                        + "licenseCheck {\n"
                        + "    allowUnknown = true\n"
                        + "}\n");
        BuildResult allowed = runner("checkLicenses").build();
        assertEquals(SUCCESS, allowed.task(":checkLicenses").getOutcome());
        assertTrue(reportText().contains("global:allow-unknown"), reportText());
    }

    @Test
    void coordinateOverrideReplacesGlobalListsForMatchingComponent() throws IOException {
        FakeMavenRepo.Coords gplLib = FakeMavenRepo.coords("com.legacy:gpl-thing:4.2");
        FakeMavenRepo.Coords gplTransitive = FakeMavenRepo.coords("org.legacy:gpl-helper:0.9");
        repo.publish(gplTransitive, List.of(GPL3), List.of());
        repo.publish(gplLib, List.of(GPL3), List.of(gplTransitive));

        writeBuildFile(
                "dependencies { implementation 'com.legacy:gpl-thing:4.2' }\n"
                        + "licenseCheck {\n"
                        + "    denyLicenses = ['" + GPL3 + "']\n"
                        + "    rules {\n"
                        // exact GAV match: explicitly allow this one component
                        + "        register('com.legacy:gpl-thing:4.2') {\n"
                        + "            allow '" + GPL3 + "'\n"
                        + "        }\n"
                        // group:name match (any version): explicitly allow unknown metadata
                        + "        register('org.legacy:gpl-helper') {\n"
                        + "            allow '" + GPL3 + "'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = runner("checkLicenses").build();
        assertEquals(SUCCESS, result.task(":checkLicenses").getOutcome());
        String text = reportText();
        assertTrue(text.contains("com.legacy:gpl-thing:4.2"), text);
        assertTrue(text.contains("coordinate-rule:com.legacy:gpl-thing:4.2:allow:" + GPL3), text);
        assertTrue(text.contains("coordinate-rule:org.legacy:gpl-helper:allow:" + GPL3),
                "group:name override must apply regardless of version");
        assertFalse(text.contains("Verdict   : DENIED"), text);
        assertTrue(reportJson().contains("coordinate-rule:com.legacy:gpl-thing:4.2"));

        // Without the override the same dependency graph must fail on the blacklist
        writeBuildFile(
                "dependencies { implementation 'com.legacy:gpl-thing:4.2' }\n"
                        + "licenseCheck {\n"
                        + "    denyLicenses = ['" + GPL3 + "']\n"
                        + "}\n");
        BuildResult failed = runner("checkLicenses").buildAndFail();
        assertEquals(FAILED, failed.task(":checkLicenses").getOutcome());
    }
}

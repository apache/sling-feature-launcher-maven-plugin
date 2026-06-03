/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.maven.feature.launcher;

import java.io.File;
import java.io.IOException;

import org.apache.maven.model.Dependency;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LaunchTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Dependency validDep;
    private File validFeatureFile;

    @Before
    public void prepare() throws IOException {
        validDep = new Dependency();
        validDep.setGroupId("org.apache.sling");
        validDep.setArtifactId("org.apache.sling.starter");
        validDep.setVersion("12");
        validDep.setClassifier("oak_tar");
        validDep.setType("slingosgifeature");

        validFeatureFile = tmp.newFile("feature.json");
    }

    @Test
    public void validLaunch_withFeature() {

        Launch launch = new Launch();
        launch.setFeature(validDep);
        launch.setId("oak_TAR-12.1"); // covers all allowed character classes
        launch.validate();
        assertSame(validDep, launch.getFeature().orElse(null));
    }

    @Test
    public void validLaunch_withFeatureFile() {

        Launch launch = new Launch();
        launch.setFeatureFile(validFeatureFile.getAbsolutePath());
        launch.setId("feature");
        launch.validate();
        assertEquals(validFeatureFile, launch.getFeatureFile().orElse(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_noId() {

        Launch launch = new Launch();
        launch.setFeature(validDep);
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_unsupportedType() {

        Dependency invalidDep = validDep.clone();
        invalidDep.setType("jar");

        Launch launch = new Launch();
        launch.setFeature(invalidDep);
        launch.setId("oak_tar");
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_noFeature() {

        Launch launch = new Launch();
        launch.setId("no_feature");
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_badId() {

        Launch launch = new Launch();
        launch.setId("/feature");
        launch.setFeature(validDep);
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_negativeTimeout() {

        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeature(validDep);
        launch.setStartTimeoutSeconds(-10);
        launch.validate();
    }

    @Test
    public void validLaunch_bothFeatureAndFeatureFile() {

        // Combining a feature (Maven coords) and a featureFile (local file path) is now
        // allowed — they are both additive inputs to the launcher's feature aggregation,
        // not mutually exclusive single-feature selectors.
        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeature(validDep);
        launch.setFeatureFile(validFeatureFile.getAbsolutePath());
        launch.validate();
        assertTrue(launch.getFeature().isPresent());
        assertTrue(launch.getFeatureFile().isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_missingFeatureFile() {

        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatureFile(validFeatureFile.getAbsolutePath() + ".missing");
        launch.validate();
    }

    @Test
    public void validLaunch_multipleFeatures() {
        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatures(java.util.Arrays.asList(validDep, validDep));
        launch.validate();
        assertEquals(2, launch.getFeatures().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_missingEntryInFeatureFiles() {
        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatureFiles(java.util.Arrays.asList(
                validFeatureFile.getAbsolutePath(), validFeatureFile.getAbsolutePath() + ".missing"));
        launch.validate();
    }

    @Test
    public void validLaunch_osgiBsnCollisionDetection() {
        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatureFile(validFeatureFile.getAbsolutePath());

        assertFalse(launch.isOsgiBsnCollisionDetection());
        launch.setOsgiBsnCollisionDetection(true);
        launch.validate();
        assertTrue(launch.isOsgiBsnCollisionDetection());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_featuresEntryWithWrongType() {
        Dependency invalidDep = validDep.clone();
        invalidDep.setType("jar");

        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatures(java.util.Arrays.asList(invalidDep));
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_blankEntryInFeatureFiles() {
        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatureFiles(java.util.Arrays.asList(validFeatureFile.getAbsolutePath(), "   "));
        launch.validate();
    }

    @Test
    public void settersAndGetters() {
        Launch launch = new Launch();

        launch.setSkip(true);
        assertTrue(launch.isSkip());

        launch.setStartTimeoutSeconds(120);
        assertEquals(120, launch.getStartTimeoutSeconds());

        launch.setArtifactClashOverrides(java.util.Arrays.asList("*:*:HIGHEST"));
        assertEquals(1, launch.getArtifactClashOverrides().size());

        launch.setRepositoryUrls(java.util.Arrays.asList("file:///tmp"));
        assertEquals(1, launch.getRepositoryUrls().size());

        LauncherArguments la = new LauncherArguments();
        launch.setLauncherArguments(la);
        assertSame(la, launch.getLauncherArguments());
    }

    @Test
    public void environmentVariablesDefaultEmptyAndAccepts() {
        Launch launch = new Launch();
        assertTrue(launch.getEnvironmentVariables().isEmpty());

        launch.setEnvironmentVariables(java.util.Collections.singletonMap("FOO", "bar"));
        assertEquals("bar", launch.getEnvironmentVariables().get("FOO"));

        // null-defensive read path.
        launch.setEnvironmentVariables(null);
        assertTrue(launch.getEnvironmentVariables().isEmpty());
    }

    @Test
    public void featureFilesGetterIsNullSafe() {
        // Default — no featureFiles configured — returns an empty list.
        assertTrue(new Launch().getFeatureFiles().isEmpty());

        // Explicit null — same defensive behaviour.
        Launch nullCase = new Launch();
        nullCase.setFeatureFiles(null);
        assertTrue(nullCase.getFeatureFiles().isEmpty());
    }

    @Test
    public void artifactClashOverridesGetterIsNullSafe() {
        assertTrue(new Launch().getArtifactClashOverrides().isEmpty());

        Launch nullCase = new Launch();
        nullCase.setArtifactClashOverrides(null);
        assertTrue(nullCase.getArtifactClashOverrides().isEmpty());
    }

    @Test
    public void featuresGetterIsNullSafe() {
        assertTrue(new Launch().getFeatures().isEmpty());

        Launch nullCase = new Launch();
        nullCase.setFeatures(null);
        assertTrue(nullCase.getFeatures().isEmpty());
    }
}

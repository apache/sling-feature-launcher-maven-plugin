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

public class LaunchTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    private Dependency validDep;
    private File validFeatureFile;

    @Before
    public void prepare() throws IOException {
        validDep  = new Dependency();
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
    }

    @Test
    public void validLaunch_withFeatureFile() {
        
        Launch launch = new Launch();
        launch.setFeatureFile(validFeatureFile.getAbsolutePath());
        launch.setId("feature");
        launch.validate();
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
    
    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_bothFeatureAndFeatureFile() {

        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeature(validDep);
        launch.setFeatureFile(validFeatureFile.getAbsolutePath());
        launch.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLaunch_missingFeatureFile() {

        Launch launch = new Launch();
        launch.setId("feature");
        launch.setFeatureFile(validFeatureFile.getAbsolutePath() + ".missing");
        launch.validate();
    }
}

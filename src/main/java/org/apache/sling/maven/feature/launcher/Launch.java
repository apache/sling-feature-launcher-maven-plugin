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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;

public class Launch {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-\\.]+");

    private String id;
    private Dependency feature;
    private List<Dependency> features = new ArrayList<>();
    private String featureFile;
    private List<String> featureFiles = new ArrayList<>();
    private boolean osgiBsnCollisionDetection;
    private List<String> artifactClashOverrides = new ArrayList<>();
    private LauncherArguments launcherArguments = new LauncherArguments();
    private int startTimeoutSeconds = 30;
    private boolean skip = false;
    private Map<String, String> environmentVariables = new HashMap<>();
    private List<String> repositoryUrls = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Optional<Dependency> getFeature() {
        return Optional.ofNullable(feature);
    }

    public void setFeature(Dependency feature) {
        this.feature = feature;
    }

    public Optional<File> getFeatureFile() {
        return Optional.ofNullable(featureFile).map(File::new);
    }

    public void setFeatureFile(String featureFile) {
        this.featureFile = featureFile;
    }

    /** Additional features (Maven coords) to aggregate alongside {@link #getFeature()}. */
    public List<Dependency> getFeatures() {
        return features != null ? features : Collections.emptyList();
    }

    public void setFeatures(List<Dependency> features) {
        this.features = features;
    }

    /** Additional feature files to aggregate alongside {@link #getFeatureFile()}. */
    public List<File> getFeatureFiles() {
        return featureFiles == null
                ? Collections.emptyList()
                : featureFiles.stream().map(File::new).collect(java.util.stream.Collectors.toList());
    }

    public void setFeatureFiles(List<String> featureFiles) {
        this.featureFiles = featureFiles;
    }

    /** @return whether to enable OSGi BSN collision detection in the launcher. Default false. */
    public boolean isOsgiBsnCollisionDetection() {
        return osgiBsnCollisionDetection;
    }

    public void setOsgiBsnCollisionDetection(boolean enabled) {
        this.osgiBsnCollisionDetection = enabled;
    }

    /** Maven-coord clash overrides passed to the launcher as {@code -C} flags. */
    public List<String> getArtifactClashOverrides() {
        return artifactClashOverrides == null ? Collections.emptyList() : artifactClashOverrides;
    }

    public void setArtifactClashOverrides(List<String> artifactClashOverrides) {
        this.artifactClashOverrides = artifactClashOverrides;
    }

    public LauncherArguments getLauncherArguments() {
        return launcherArguments;
    }

    public void setLauncherArguments(LauncherArguments launcherArguments) {
        this.launcherArguments = launcherArguments;
    }

    public int getStartTimeoutSeconds() {
        return startTimeoutSeconds;
    }

    public void setStartTimeoutSeconds(int startTimeoutSeconds) {
        this.startTimeoutSeconds = startTimeoutSeconds;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public Map<String, String> getEnvironmentVariables() {
        if (environmentVariables == null) return Collections.emptyMap();
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public List<String> getRepositoryUrls() {
        return repositoryUrls;
    }

    public void setRepositoryUrls(List<String> repositoryUrls) {
        this.repositoryUrls = repositoryUrls;
    }

    public void validate() {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Missing id");

        if (!ID_PATTERN.matcher(id).matches())
            throw new IllegalArgumentException(
                    "Invalid id '" + id + "'. Allowed characters are digits, numbers, '-','_' and '.'.");

        if (startTimeoutSeconds < 0) throwInvalid("startTimeout value '" + startTimeoutSeconds + "' is negative");

        boolean hasFeature = feature != null;
        boolean hasFeatureFile = featureFile != null && !featureFile.trim().isEmpty();
        boolean hasFeatures = features != null && !features.isEmpty();
        boolean hasFeatureFiles = featureFiles != null && !featureFiles.isEmpty();

        if (!hasFeature && !hasFeatureFile && !hasFeatures && !hasFeatureFiles) {
            throwInvalid("None of 'feature', 'featureFile', 'features', 'featureFiles' are set");
        }

        if (hasFeatureFile && !new File(featureFile).exists())
            throwInvalid("Feature file '" + featureFile + "' does not exist");

        if (hasFeature && !"slingosgifeature".equals(feature.getType()))
            throwInvalid("type must be 'slingosgifeature' but is '" + feature.getType() + "'");

        if (hasFeatures) {
            for (final Dependency d : features) {
                if (!"slingosgifeature".equals(d.getType())) {
                    throwInvalid("features[].type must be 'slingosgifeature' but is '" + d.getType() + "'");
                }
            }
        }

        if (hasFeatureFiles) {
            for (final String f : featureFiles) {
                if (f == null || f.trim().isEmpty()) {
                    throwInvalid("featureFiles[] entry is blank");
                }
                if (!new File(f).exists()) {
                    throwInvalid("Feature file '" + f + "' does not exist");
                }
            }
        }
    }

    private void throwInvalid(String reason) {
        throw new IllegalArgumentException("Invalid launch '" + id + "': " + reason);
    }
}

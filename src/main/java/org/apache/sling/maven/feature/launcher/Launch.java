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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;

public class Launch {
    
    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-\\.]+");

    private String id;
    private Dependency feature;
    private LauncherArguments launcherArguments = new LauncherArguments();
    private int startTimeoutSeconds = 30;
    private boolean skip = false;
    private Map<String,String> environmentVariables = new HashMap<>();
    private List<String> repositoryUrls = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Dependency getFeature() {
        return feature;
    }

    public void setFeature(Dependency feature) {
        this.feature = feature;
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
        if ( environmentVariables == null )
            return Collections.emptyMap();
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
        if ( id == null || id.trim().isEmpty() ) 
            throw new IllegalArgumentException("Missing id");
        
        if ( !ID_PATTERN.matcher(id).matches() )
            throw new IllegalArgumentException("Invalid id '" + id + "'. Allowed characters are digits, numbers, '-','_' and '.'.");
        
        if ( startTimeoutSeconds < 0 )
            throwInvalid("startTimeout value '" + startTimeoutSeconds + "' is negative" );
        
        if ( feature == null )
            throwInvalid("required field 'feature' is missing");
        
        if ( ! "slingosgifeature".equals(feature.getType()) )
            throwInvalid("type must be 'slingosgifeature' but is '" + feature.getType()+"'");
    }
    
    private void throwInvalid(String reason) {
        throw new IllegalArgumentException("Invalid launch '" + id + "': " + reason);
    }
}

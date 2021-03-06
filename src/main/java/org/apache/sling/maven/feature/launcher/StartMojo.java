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


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo( name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST )
public class StartMojo extends AbstractMojo {
    
    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;

    @Parameter( required = true, defaultValue = "1.1.4")
    private String featureLauncherVersion;
    
    @Parameter(required = true)
    private List<Launch> launches;
    
    // <launches>
    //   <launch>
    //     <id>...</id>
    //     <dependency>...</dependency>
    //     <launcherArguments>
    //        <frameworkProperties>
    //          <org.osgi.service.http.port>8090</org.osgi.service.http.port>
    //        </framweworkProperties>
    //        ...
    //     </launcherArguments>
    //     <environment>
    //        <JAVA_HOME>...</JAVA_HOME>
    //     </environment>

    @Component
    private ArtifactResolver resolver;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;
    
    @Component
    private ProcessTracker processes;
    
    public void execute() throws MojoExecutionException, MojoFailureException {

        Artifact launcherArtifact = new DefaultArtifact("org.apache.sling:org.apache.sling.feature.launcher:" + featureLauncherVersion);

        try {
            RepositorySystemSession repositorySession = mavenSession.getRepositorySession();
            File launcher = resolver
                .resolveArtifact(repositorySession, new ArtifactRequest(launcherArtifact, remoteRepos, null))
                .getArtifact()
                .getFile();
            
            File workDir = new File(outputDirectory, "launchers");
            workDir.mkdirs();
            
            for ( Launch launch : launches ) {
                if (launch.isSkip()) {
                    getLog().info("Skipping starting launch with id " + launch.getId());
                    continue; // skip it
                }

                launch.validate();

                Artifact artifact = toArtifact(launch.getFeature());
                
                ArtifactResult result = resolver.resolveArtifact(repositorySession, new ArtifactRequest(artifact, remoteRepos, null));
                File featureFile = result.getArtifact().getFile();
                
                List<String> args = new ArrayList<>();
                String javahome = System.getenv("JAVA_HOME");
                if (javahome == null || javahome.isEmpty()) {
                    // SLING-9843 fallback to java.home system property if JAVA_HOME env variable is not set
                    getLog().warn("The JAVA_HOME env variable was not set, falling back to the java.home system property");
                    javahome = System.getProperty("java.home");
                }
                args.add(javahome + File.separatorChar + "bin" + File.separatorChar + "java");
                // SLING-9994 - if any extra vm options were supplied, apply them here
                String[] vmOptions = launch.getLauncherArguments().getVmOptions();
                if (vmOptions != null) {
                    for (String vmOption : vmOptions) {
                        if (vmOption != null && !vmOption.isEmpty()) {
                            args.add(vmOption);
                        }
                    }
                }
                args.add("-jar");
                args.add(launcher.getAbsolutePath());
                args.add("-f");
                args.add(featureFile.getAbsolutePath());
                args.add("-p");
                args.add(launch.getId());
                
                for ( Map.Entry<String, String> frameworkProperty : launch.getLauncherArguments().getFrameworkProperties().entrySet() ) {
                    args.add("-D");
                    args.add(frameworkProperty.getKey()+"="+frameworkProperty.getValue());
                }
                
                for ( Map.Entry<String, String> variable : launch.getLauncherArguments().getVariables().entrySet() ) {
                    args.add("-V");
                    args.add(variable.getKey()+"="+variable.getValue());
                }

                // TODO - add support for all arguments supported by the feature launcher
                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectOutput(Redirect.INHERIT);
                pb.redirectInput(Redirect.INHERIT);
                pb.directory(workDir);
                
                getLog().info("Starting launch with id '" + launch.getId() + "', args=" + args);
                
                CountDownLatch latch = new CountDownLatch(1);
                
                Process process = pb.start();
                
                Thread monitor = new Thread("launch-monitor-" + launch.getId()) {
                    @Override
                    public void run() {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line;
                        try {
                            while ( (line = reader.readLine()) != null ) {
                                System.out.println(line); // NOSONAR - we pass through the subprocess stderr
                                if ( line.contains("Framework started")) {
                                    latch.countDown();
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            getLog().warn(e.getMessage(), e);
                        }
                    }
                };
                monitor.start();
                getLog().info("Waiting for " + launch.getId() + " to start");
                boolean started = latch.await(launch.getStartTimeoutSeconds(), TimeUnit.SECONDS);
                if ( !started ) {
                    ProcessTracker.stop(process);
                    throw new MojoExecutionException("Launch " + launch.getId() + " failed to start in " + launch.getStartTimeoutSeconds() + " seconds.");
                }
                
                processes.startTracking(launch.getId(), process);
            }

        } catch (ArtifactResolutionException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Execution interrupted", e);
        }
    }

    private Artifact toArtifact(Dependency dependency) {
        return new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion());
    }
}

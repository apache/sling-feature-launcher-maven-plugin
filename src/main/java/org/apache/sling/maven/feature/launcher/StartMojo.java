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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
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
import org.apache.maven.shared.utils.Os;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Start one or multiple <a href="https://sling.apache.org/documentation/development/feature-model.html">Sling Feature(s)</a>.
 */
@Mojo( name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST )
public class StartMojo extends AbstractMojo {

    private static final String JAVA_HOME = "JAVA_HOME";
    private static final String JAVA_OPTS = "JAVA_OPTS";

    /**
     * The directory in which the features are launched (below its child directory {@code launchers/<launch-id>}).
     */
    @Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;

    /**
     * The version of the <a href="https://github.com/apache/sling-org-apache-sling-feature-launcher">Sling Feature Launcher</a> to use.
     */
    @Parameter( required = true, defaultValue = "1.1.4")
    private String featureLauncherVersion;
    
    // TODO: extract this field into common parent class
    /**
     * List of {@link Launch} objects to start. Each is having the following format:
     * <pre>{@code
     * <id>...</id> <!-- the id of the launch, must be unique within the list, is mandatory -->
     * <dependency>...</dependency> <!-- the Maven coordinates of the feature model -->
     * <launcherArguments> <!-- additional arguments to pass to the launcher -->
     *   <frameworkProperties>
     *     <org.osgi.service.http.port>8090</org.osgi.service.http.port>
     *   </framweworkProperties>
     *   ..
     * </launcherArguments>
     * <environmentVariables><!--additional environment variables to pass to the launcher -->
     *  <JAVA_HOME>...</JAVA_HOME>
     * </environmentVariables>}
     * </pre>
     */
    @Parameter(required = true)
    private List<Launch> launches;

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

    /**
     * To look up UnArchiver implementations
     */
    @Component
    private ArchiverManager archiverManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            // the feature launcher before version 1.1.28 used a single jar, while versions
            //  after that provide an assembly per SLING-10956
            VersionRange beforeAssemblyRange = VersionRange.createFromVersionSpec("(,1.1.26]");
            boolean useAssembly = !beforeAssemblyRange.containsVersion(new DefaultArtifactVersion(featureLauncherVersion));

            RepositorySystemSession repositorySession = mavenSession.getRepositorySession();
            File workDir = new File(outputDirectory, "launchers");
            workDir.mkdirs();

            File launcher;
            if (useAssembly) {
                // fetch the assembly artifact
                Artifact launcherAssemblyArtifact = new DefaultArtifact("org.apache.sling:org.apache.sling.feature.launcher:tar.gz:" + featureLauncherVersion);
                File assemblyArchive = resolver
                        .resolveArtifact(repositorySession, new ArtifactRequest(launcherAssemblyArtifact, remoteRepos, null))
                        .getArtifact()
                        .getFile();

                // unpack the file
                UnArchiver unArchiver = archiverManager.getUnArchiver( assemblyArchive );
                unArchiver.setSourceFile(assemblyArchive);
                unArchiver.setDestFile(workDir);
                unArchiver.extract();

                // system property
                Path relPath = Paths.get(launcherAssemblyArtifact.getArtifactId() + "-" + launcherAssemblyArtifact.getVersion(), "bin");
                if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                    relPath = relPath.resolve("launcher.bat");
                } else {
                    relPath = relPath.resolve("launcher");
                }
                launcher = workDir.toPath().resolve(relPath).toFile();
            } else {
                Artifact launcherArtifact = new DefaultArtifact("org.apache.sling:org.apache.sling.feature.launcher:" + featureLauncherVersion);
                launcher = resolver
                        .resolveArtifact(repositorySession, new ArtifactRequest(launcherArtifact, remoteRepos, null))
                        .getArtifact()
                        .getFile();
            }
            
            for ( Launch launch : launches ) {
                if (launch.isSkip()) {
                    getLog().info("Skipping starting launch with id " + launch.getId());
                    continue; // skip it
                }

                launch.validate();

                Artifact artifact = toArtifact(launch.getFeature());
                
                ArtifactResult result = resolver.resolveArtifact(repositorySession, new ArtifactRequest(artifact, remoteRepos, null));
                File featureFile = result.getArtifact().getFile();

                String javahome = System.getenv(JAVA_HOME);
                if (javahome == null || javahome.isEmpty()) {
                    // SLING-9843 fallback to java.home system property if JAVA_HOME env variable is not set
                    getLog().warn("The JAVA_HOME env variable was not set, falling back to the java.home system property");
                    javahome = System.getProperty("java.home");
                }
                List<String> args = new ArrayList<>();
                if (useAssembly) {
                    // use the post v1.1.28 launcher script

                    Map<String, String> newEnv = new HashMap<>(launch.getEnvironmentVariables());
                    newEnv.put(JAVA_HOME, javahome);

                    // SLING-9994 - if any extra vm options were supplied, apply them here
                    StringBuilder javaOptsBuilder = null;
                    String[] vmOptions = launch.getLauncherArguments().getVmOptions();
                    for (String vmOption : vmOptions) {
                        if (vmOption != null && !vmOption.isEmpty()) {
                            if (javaOptsBuilder == null) {
                                javaOptsBuilder = new StringBuilder();
                            } else {
                                javaOptsBuilder.append(" ");
                            }
                            javaOptsBuilder.append(vmOption);
                        }
                    }
                    if (javaOptsBuilder != null) {
                        // pass vmOptions through JAVA_OPTS environment variable?
                        if (newEnv.containsKey(JAVA_OPTS)) {
                            // if the original value existed append it to our buffer
                            javaOptsBuilder.append(" ").append(newEnv.get(JAVA_OPTS));
                        }
                        newEnv.put(JAVA_OPTS, javaOptsBuilder.toString());
                    }

                    args.add(launcher.getAbsolutePath());

                    launch.setEnvironmentVariables(newEnv);
                } else {
                    // use the pre v1.1.28 single jar technique

                    args.add(javahome + File.separatorChar + "bin" + File.separatorChar + "java");
                    // SLING-9994 - if any extra vm options were supplied, apply them here
                    String[] vmOptions = launch.getLauncherArguments().getVmOptions();
                    for (String vmOption : vmOptions) {
                        if (vmOption != null && !vmOption.isEmpty()) {
                            args.add(vmOption);
                        }
                    }
                    args.add("-jar");
                    args.add(launcher.getAbsolutePath());
                }
                if ( launch.getRepositoryUrls() != null && !launch.getRepositoryUrls().isEmpty() ) {
                    args.add("-u");
                    StringJoiner joiner = new StringJoiner(",");
                    launch.getRepositoryUrls().forEach( joiner::add );
                    args.add(joiner.toString());
                }
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
                launch.getEnvironmentVariables().entrySet()
                    .forEach( e -> {
                            getLog().info("Setting environment variable '" + e.getKey() + "' to '" + e.getValue() + "'");
                            pb.environment().put(e.getKey(), e.getValue());
                        } );
                
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

        } catch (NoSuchArchiverException | InvalidVersionSpecificationException | ArtifactResolutionException | IOException e) {
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

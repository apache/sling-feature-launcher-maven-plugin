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

import java.util.List;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

/**
 * Stop one or multiple <a href="https://sling.apache.org/documentation/development/feature-model.html">Sling Feature(s)</a>.
 */
@Mojo( name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractMojo {

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
    private ProcessTracker processes;
    
    /**
     * If {@code true} stopping the server is deferred until you press the Enter key on the terminal on which Maven is executed.
     */
    @Parameter(property = "feature-launcher.waitForInput", required = false, defaultValue = "false")
    protected boolean waitForInput;

    @Component
    private Prompter prompter;

    @Parameter(defaultValue = "${session.request}", readonly = true)
    private MavenExecutionRequest executionRequest;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (waitForInput) {
            if (executionRequest.isInteractiveMode()) {
                waitForUserInput();
            } else {
                getLog().warn("Don't wait for user input as Maven is not running in interactive mode");
            }
        }
        try {
            for ( Launch launch : launches ) {
                if (launch.isSkip()) {
                    getLog().info("Skipping stopping launch with id " + launch.getId());
                    continue; // skip it
                }

                getLog().info("Stopping launch with id " + launch.getId());
                processes.stop(launch.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void waitForUserInput() throws MojoFailureException {
        // http://stackoverflow.com/a/21977269/5155923
        try {
            String message = MessageUtils.buffer().warning("Waiting for user input before build continues...").toString();
            getLog().warn(message);
            prompter.prompt("Press Enter to continue");
        } catch (PrompterException e) {
            throw new MojoFailureException("Could not prompt for user input. Do not use parameter 'waitForInput' in that case", e);
        }
    }
}

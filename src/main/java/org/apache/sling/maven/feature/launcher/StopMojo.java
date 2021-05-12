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

@Mojo( name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractMojo {

    @Parameter(required = true)
    private List<Launch> launches;
    
    @Component
    private ProcessTracker processes;
    
    /**
     * If {@code true} stopping the server is deferred until you press the Enter key.
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
                waitForInput();
            } else {
                getLog().warn("Don't wait for user input as Maven is not running in interactive mode");
            }
        }
        try {
            for ( Launch launch : launches ) {
                getLog().info("Stopping launch with id " + launch.getId());
                processes.stop(launch.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void waitForInput() throws MojoFailureException {
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

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.shared.utils.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
public class ProcessTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTracker.class);

    static void stop(Process process) throws InterruptedException {
        LOG.debug("Destroy process: {}", process);
        process.destroy();
        boolean stopped = process.waitFor(30, TimeUnit.SECONDS);
        if ( !stopped ) {
            LOG.debug("Forcibly destroy process after 30sec: {}", process);
            process.destroyForcibly();
        }
        LOG.debug("Destroy process finished: {}", process);
    }

    /**
     * On windows, this method is used for stopping the launcher.
     * The Launcher is started from a .bat file, and killing the known process only kills the .bat process, not the spawned java process.
     * So we try to kill all descendant processes first.
     */
    static void stopWithDescendants(Process process) throws InterruptedException {
        LOG.debug("Destroy process with descendants: {}", process);

        ProcessHandle processHandle = ProcessHandle.of(process.pid()).orElse(null);
        if (processHandle == null) {
            LOG.error("Unable to shutdown process, no process handle for pid {}", process.pid());
            return;
        }

        processHandle.descendants().forEach(childProcess -> {
            LOG.debug("Destroy child process: {}", childProcess);
            childProcess.destroy();
            try {
                boolean stopped = childProcess.onExit().get(30, TimeUnit.SECONDS) != null;
                if ( !stopped ) {
                    LOG.debug("Forcibly destroy child process after 30sec: {}", childProcess);
                    childProcess.destroyForcibly();
                }
                LOG.debug("Destroy child process finished: {}", childProcess);
            } catch (Exception ex) {
                LOG.error("Error while stopping child process {}: {}", childProcess, ex.getMessage(), ex);
            }
        });

        stop(process);
    }

    private final Object sync = new Object();

    private boolean hookAdded = false;
    private final Map<String, Process> processes = new HashMap<>();

    public void startTracking(String launchId, Process process) {
        synchronized (sync) {
            if ( processes.containsKey(launchId) )
                throw new IllegalArgumentException("Launch id " + launchId + " already associated with a process");
            LOG.debug("Start tracking process for launch {}: {}", launchId, process);
            processes.put(launchId, process);
            if ( ! hookAdded ) {
                Runtime.getRuntime().addShutdownHook(new Thread("process-tracker-shutdown") {
                    @Override
                    public void run() {
                        LOG.debug("Shutdown hook  is running for launch {}: {}", launchId, process);
                        for ( Map.Entry<String, Process> entry : processes.entrySet() ) {
                            LOG.error("Launch {} was not shut down! Destroying forcibly from shutdown hook.", entry.getKey());
                            process.destroyForcibly();
                        }
                    }

                });
                hookAdded = true;
            }
        }
    }

    public void stop(String id) throws InterruptedException {
        Process process;
        synchronized (sync) {
            process = processes.remove(id);
        }
        if ( process == null ) {
            LOG.warn("Process not found in process list, skip stopping: {}", id);
            return;
        }

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            stopWithDescendants(process);
        }
        else {
            stop(process);
        }
    }
}

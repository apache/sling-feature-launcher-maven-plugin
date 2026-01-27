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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.utils.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
public class ProcessTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTracker.class);

    static void stop(Process process) throws InterruptedException {
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            stopWithDescendants(process, false);
        } else {
            stopDirectly(process, false);
        }
    }

    static void stopForcibly(Process process) throws InterruptedException {
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            stopWithDescendants(process, true);
        } else {
            stopDirectly(process, true);
        }
    }

    /**
     * Use this for non-windows OS only by stopping the process directly, not caring about descendants.
     */
    private static void stopDirectly(Process process, boolean forcibly) throws InterruptedException {
        if (forcibly) {
            LOG.debug("Forcibly destroy process: {}", process);
            process.destroyForcibly();
            return;
        }
        LOG.debug("Destroy process: {}", process);
        process.destroy();
        boolean stopped = process.waitFor(30, TimeUnit.SECONDS);
        if (!stopped) {
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
    private static void stopWithDescendants(Process process, boolean forcibly) throws InterruptedException {
        LOG.debug("Destroy process with descendants: {}", process);

        ProcessHandle processHandle = ProcessHandle.of(process.pid()).orElse(null);
        if (processHandle == null) {
            LOG.error("Unable to shutdown process, no process handle for pid {}", process.pid());
            return;
        }

        for (ProcessHandle childProcess : processHandle.descendants().collect(Collectors.toList())) {
            if (forcibly) {
                LOG.debug("Forcibly destroy child process: {}", childProcess);
                childProcess.destroyForcibly();
                return;
            }
            LOG.debug("Destroy child process: {}", childProcess);
            childProcess.destroy();
            try {
                boolean stopped = childProcess.onExit().get(30, TimeUnit.SECONDS) != null;
                if (!stopped) {
                    LOG.debug("Forcibly destroy child process after 30sec: {}", childProcess);
                    childProcess.destroyForcibly();
                }
                LOG.debug("Destroy child process finished: {}", childProcess);
            } catch (TimeoutException | ExecutionException ex) {
                LOG.error("Error while stopping child process {}: {}", childProcess, ex.getMessage(), ex);
            }
        }

        stopDirectly(process, forcibly);
    }

    private final Object sync = new Object();

    private boolean hookAdded = false;
    private final Map<String, Process> processes = new HashMap<>();
    private Path tempRepository;

    public void startTracking(String launchId, Process process) {
        synchronized (sync) {
            if (processes.containsKey(launchId))
                throw new IllegalArgumentException("Launch id " + launchId + " already associated with a process");
            LOG.debug("Start tracking process for launch {}: {}", launchId, process);
            processes.put(launchId, process);
            if (!hookAdded) {
                Runtime.getRuntime().addShutdownHook(new Thread("process-tracker-shutdown") {
                    @Override
                    public void run() {
                        LOG.debug("Shutdown hook  is running for launch {}: {}", launchId, process);
                        for (Map.Entry<String, Process> entry : processes.entrySet()) {
                            LOG.error(
                                    "Launch {} was not shut down! Destroying forcibly from shutdown hook.",
                                    entry.getKey());
                            try {
                                ProcessTracker.stopForcibly(process);
                            } catch (InterruptedException e) {
                                interrupt();
                            }
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
        if (process == null) {
            LOG.warn("Process not found in process list, skip stopping: {}", id);
            return;
        }
        ProcessTracker.stop(process);
    }

    /**
     * Sets the path to the temporary repository containing attached artifacts.
     * @param tempRepository the path to the temporary repository
     */
    public void setTempRepository(Path tempRepository) {
        this.tempRepository = tempRepository;
    }

    /**
     * Deletes the temporary repository with all its contents.
     */
    public void deleteTempRepository() {
        if (this.tempRepository != null && Files.exists(this.tempRepository)) {
            try {
                LOG.info("Deleting temporary artifact repository at: {}", this.tempRepository);
                FileUtils.deleteDirectory(this.tempRepository.toFile());
                this.tempRepository = null;
            } catch (IOException ex) {
                LOG.warn("Failed to delete temporary artifact repository at {}", this.tempRepository, ex);
            }
        }
    }

}

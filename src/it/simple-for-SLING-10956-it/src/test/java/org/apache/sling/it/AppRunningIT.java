/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.it;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.TestMethodOrder;
 import  org.apache.http.impl.client.*;
import  org.apache.http.client.methods.*;

@TestMethodOrder(Alphanumeric.class)
public class AppRunningIT {

    @Test
    public void aaSlingAppIsUp() throws Exception {

        int port = Integer.getInteger("HTTP_PORT", 8080);

        try ( CloseableHttpClient httpclient = HttpClients.createDefault() ) {
            HttpGet get = new HttpGet("http://localhost:" + port + "/");
            for ( int i = 0; i < 30; i++ ) {
                try ( CloseableHttpResponse response = httpclient.execute(get) ) {
                    System.out.println("Status line = " + response.getStatusLine().toString());
                    int statusCode = response.getStatusLine().getStatusCode();
                    if ( (statusCode / 100 < 5 ) ) {
                        System.out.println("App is ready");
                        return;
                    }
                    Thread.sleep(1000l);
                }
            }

            fail("App is not yet ready, failing");
        }
    }

    @Test
    public void bbCheckLauncherEnvironmentVarInLogs() throws Exception {
        final String logFilename = System.getProperty("build.log.file");

        // This verifies the launcherArguments vmOptions and variables from our test pom
        final Pattern expected = Pattern.compile(".*\\-DTEST_VM_OPTION=TEST_VM_OPTION_VALUE.*");

        try (Stream<String> lines = Files.lines(Paths.get(logFilename))) {
            final Optional<String> expectedLine = lines.filter(line -> expected.matcher(line).matches()).findFirst();
            assertTrue(expectedLine.isPresent(), "Expected pattern " + expected + " to be found in log file " + logFilename);
        }
    }

    @Test
    public void bbCheckLauncherCommandLineInLogs() throws Exception {
        final String logFilename = System.getProperty("build.log.file");

        // This verifies the launcherArguments vmOptions and variables from our test pom
        final Pattern expected = Pattern.compile(".*\\-V, TEST_VARIABLE=TEST_VALUE.*");

        try (Stream<String> lines = Files.lines(Paths.get(logFilename))) {
            final Optional<String> expectedLine = lines.filter(line -> expected.matcher(line).matches()).findFirst();
            assertTrue(expectedLine.isPresent(), "Expected pattern " + expected + " to be found in log file " + logFilename);
        }
    }
}
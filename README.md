[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-feature-launcher-maven-plugin/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-feature-launcher-maven-plugin/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-feature-launcher-maven-plugin/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-feature-launcher-maven-plugin/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-feature-launcher-maven-plugin&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-feature-launcher-maven-plugin)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-feature-launcher-maven-plugin&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-feature-launcher-maven-plugin)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/feature-launcher-maven-plugin.svg)](https://www.javadoc.io/doc/org.apache.sling/feature-launcher-maven-plugin)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/feature-launcher-maven-plugin/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22feature-launcher-maven-plugin%22) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

This module is part of the [Apache Sling](https://sling.apache.org) project.

This plugin allows starting and stopping feature model applications without blocking the Maven
execution. It is intended mainly for usage with integration tests. The full Maven site documenting this plugin is at <https://sling.apache.org/components/sling-feature-launcher-maven-plugin/>.

## Usage

Configure the plugin as follows:

```
<plugin>
    <groupId>org.apache.sling</groupId>
    <artifactId>feature-launcher-maven-plugin</artifactId>
    <configuration>
        <launches>
            <launch>
                <id>model</id>
                <!-- optionally uncomment to skip this launch if the skip property resolves to false -->
                <!--
                <skip>${prop1.skip}</skip>
                -->
                <feature>
                    <groupId>org.apache.sling</groupId>
                    <artifactId>org.apache.sling.starter</artifactId>
                    <version>12</version>
                    <classifier>oak_tar</classifier>
                    <type>slingosgifeature</type>
                </feature>
                <launcherArguments>
                    <!-- optionally uncomment to pass any required extra vm options -->
                    <!--
                    <vmOptions>
                        <value>-Xmx512m</value>
                        <value>-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5000</value>
                    </vmOptions>
                    -->
                    <frameworkProperties>
                        <org.osgi.service.http.port>8080</org.osgi.service.http.port>
                    </frameworkProperties>
                    <!-- Feature launcher variables can be set like this -->
                    <variables>
                        <TEST_VARIABLE>TEST_VALUE</TEST_VARIABLE>
                    </variables>
                </launcherArguments>
                <startTimeoutSeconds>180</startTimeoutSeconds>
            </launch>
        </launches>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>start</goal>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

This will run the Sling Starter using the `oak_tar` aggregate, setting the HTTP port to 8080 and
waiting for up to 180 seconds for the application to start.

The `start` goal is bound by default to the `pre-integration` phase and the `stop` goal to the
`post-integration-test` one.

See the `src/it` folder for a complete example, including a configuration of the `maven-failsafe-plugin`.

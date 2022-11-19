Sling Feature Launcher Maven Plugin
==================

The Sling Feature Launcher Maven Plugin can start and stop existing [Sling Features](https://sling.apache.org/documentation/development/feature-model.html).
It leverages the [Sling Feature Launcher](https://github.com/apache/sling-org-apache-sling-feature-launcher).

## Example Usage

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

See [Goals](plugin-info.html) for a list of supported goals.

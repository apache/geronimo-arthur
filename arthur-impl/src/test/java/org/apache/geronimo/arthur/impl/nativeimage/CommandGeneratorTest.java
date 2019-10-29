/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.arthur.impl.nativeimage;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.impl.nativeimage.graal.CommandGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.Data;

class CommandGeneratorTest {
    @ParameterizedTest
    @MethodSource("configurations")
    void generate(final Case useCase) {
        assertEquals(
                useCase.result,
                new CommandGenerator().generate(useCase.configuration));
    }

    static Stream<Case> configurations() {
        final ArthurNativeImageConfiguration emptyConfig = new ArthurNativeImageConfiguration();

        final ArthurNativeImageConfiguration classpathConfig = new ArthurNativeImageConfiguration();
        classpathConfig.setClasspath(asList("foo", "bar"));

        final ArthurNativeImageConfiguration filledConfig = new ArthurNativeImageConfiguration();
        filledConfig.setNativeImage("custom-image");
        filledConfig.setClasspath(singletonList("myclasspath"));
        filledConfig.setMaxRuntimeCompileMethods(5);
        filledConfig.setResourcesConfigurationFiles(singletonList("resources.json"));
        filledConfig.setEnforceMaxRuntimeCompileMethods(false);
        filledConfig.setAddAllCharsets(false);
        filledConfig.setReportExceptionStackTraces(false);
        filledConfig.setTraceClassInitialization(false);
        filledConfig.setPrintClassInitialization(true);
        filledConfig.setFallbackMode(ArthurNativeImageConfiguration.FallbackMode.auto);
        filledConfig.setBuildStaticImage(false);
        filledConfig.setAllowIncompleteClasspath(false);
        filledConfig.setReportExceptionStackTraces(false);
        filledConfig.setReportUnsupportedElementsAtRuntime(false);
        filledConfig.setEnableAllSecurityServices(false);
        filledConfig.setNoServer(false);
        filledConfig.setMain("mysoft");
        filledConfig.setOutput("output.bin");

        return Stream.of(
                new Case(
                        emptyConfig,
                        asList(
                                "native-image", "-classpath",
                                "-H:MaxRuntimeCompileMethods=1000", "-H:+EnforceMaxRuntimeCompileMethods",
                                "-H:+AddAllCharsets", "-H:+ReportExceptionStackTraces",
                                "-H:+TraceClassInitialization",
                                "--no-fallback", "--static", "--allow-incomplete-classpath",
                                "--report-unsupported-elements-at-runtime", "--enable-all-security-services",
                                "--no-server", "main", "main.graal.exec")),
                new Case(
                        classpathConfig,
                        asList(
                                "native-image", "-classpath", "foo" + File.pathSeparator + "bar",
                                "-H:MaxRuntimeCompileMethods=1000", "-H:+EnforceMaxRuntimeCompileMethods",
                                "-H:+AddAllCharsets", "-H:+ReportExceptionStackTraces",
                                "-H:+TraceClassInitialization",
                                "--no-fallback", "--static", "--allow-incomplete-classpath",
                                "--report-unsupported-elements-at-runtime", "--enable-all-security-services",
                                "--no-server", "main", "main.graal.exec")),
                new Case(
                        filledConfig,
                        asList(
                                "custom-image", "-classpath", "myclasspath", "-H:ResourceConfigurationFiles=resources.json",
                                "-H:MaxRuntimeCompileMethods=5", "-H:+PrintClassInitialization",
                                "--auto-fallback", "mysoft", "output.bin")));
    }

    @Data
    private static class Case {
        private final ArthurNativeImageConfiguration configuration;
        private final List<String> result;
    }
}

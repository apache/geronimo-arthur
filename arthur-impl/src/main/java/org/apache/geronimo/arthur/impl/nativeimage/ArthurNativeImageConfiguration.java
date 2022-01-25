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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;

@Data
public class ArthurNativeImageConfiguration {
    @GraalCommandPart(order = 0)
    private String nativeImage = "native-image";

    @GraalCommandPart(order = 1, passthrough = true)
    private Collection<String> customOptions = new ArrayList<>();

    @GraalCommandPart(order = 2, joiner = "${File.pathSeparator}", preParts = "-classpath")
    private Collection<String> classpath = new ArrayList<>();

    @GraalCommandPart(order = 3, joiner = ",", template = "-H:DynamicProxyConfigurationFiles=%s")
    private Collection<String> dynamicProxyConfigurationFiles = new ArrayList<>();

    @GraalCommandPart(order = 4, joiner = ",", template = "-H:ReflectionConfigurationFiles=%s")
    private Collection<String> reflectionConfigurationFiles = new ArrayList<>();

    @GraalCommandPart(order = 5, joiner = ",", template = "-H:ResourceConfigurationFiles=%s")
    private Collection<String> resourcesConfigurationFiles = new ArrayList<>();

    @GraalCommandPart(order = 6, joiner = ",", template = "-H:IncludeResourceBundles=%s")
    private Collection<String> includeResourceBundles = new ArrayList<>();

    @GraalCommandPart(order = 7, joiner = ",", template = "--initialize-at-run-time=%s")
    private Collection<String> initializeAtRunTime = new ArrayList<>();

    @GraalCommandPart(order = 8, joiner = ",", template = "--initialize-at-build-time=%s")
    private Collection<String> initializeAtBuildTime = new ArrayList<>();

    @GraalCommandPart(order = 9, template = "-H:MaxRuntimeCompileMethods=%d")
    private int maxRuntimeCompileMethods = 1000;

    @GraalCommandPart(order = 10, template = "-H:+EnforceMaxRuntimeCompileMethods")
    private boolean enforceMaxRuntimeCompileMethods = true;

    @GraalCommandPart(order = 11, template = "-H:+AddAllCharsets")
    private boolean addAllCharsets = true;

    @GraalCommandPart(order = 12, template = "-H:+ReportExceptionStackTraces")
    private boolean reportExceptionStackTraces = true;

    @GraalCommandPart(order = 14, template = "-H:+PrintClassInitialization")
    private boolean printClassInitialization = false;

    @GraalCommandPart(order = 15, template = "--%s-fallback")
    private FallbackMode fallbackMode = FallbackMode.no;

    @GraalCommandPart(order = 16, template = "--static")
    private boolean buildStaticImage = true;

    @GraalCommandPart(order = 17, template = "--allow-incomplete-classpath")
    private boolean allowIncompleteClasspath = true;

    @GraalCommandPart(order = 18, template = "--report-unsupported-elements-at-runtime")
    private boolean reportUnsupportedElementsAtRuntime = true;

    @GraalCommandPart(order = 19, template = "--enable-all-security-services")
    private Boolean enableAllSecurityServices = null;

    @GraalCommandPart(order = 20, template = "--no-server")
    private Boolean noServer = null;

    @GraalCommandPart(order = 21)
    private String main;

    @GraalCommandPart(order = 22)
    private String output;

    private boolean inheritIO = true;

    public enum FallbackMode {
        no, auto, force
    }

    @Target(FIELD)
    @Retention(RUNTIME)
    public @interface GraalCommandPart {
        /**
         * @return the order this command segment must be set to.
         */
        int order();

        /**
         * @return for collections, the separator to use to join the values.
         */
        String joiner() default "";

        /**
         * @return true if the content must be included in the command as it is provided.
         */
        boolean passthrough() default false;

        /**
         * @return command segments to prepend to the part.
         */
        String[] preParts() default {};

        /**
         * @return the template for the resulting value.
         */
        String template() default "%s";
    }
}

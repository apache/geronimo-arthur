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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
    @Deprecated
    @GraalCommandPart(order = 17, template = "--allow-incomplete-classpath")
    private Boolean allowIncompleteClasspath; // recent version make it a default
    @GraalCommandPart(order = 18, template = "--report-unsupported-elements-at-runtime")
    private boolean reportUnsupportedElementsAtRuntime = true;
    @Deprecated
    @GraalCommandPart(order = 19, template = "--enable-all-security-services")
    private Boolean enableAllSecurityServices = null;
    @GraalCommandPart(order = 20, template = "--no-server")
    private Boolean noServer = null;
    /**
     * The GraalVM version arthur builds for (set by {@link #complete(String, boolean)}).
     * Used to select the metadata emission mode (combined {@code reachability-metadata.json}
     * on modern GraalVM vs. legacy per-type {@code -H:*ConfigurationFiles}).
     */
    private String graalVersion;
    @GraalCommandPart(order = 21)
    private String main;
    @GraalCommandPart(order = 22)
    private String output;
    private boolean inheritIO = true;

    /**
     * @param graalVersion         the graalvm version used to complete this configuration, in particular allowIncompleteClasspath and enableAllSecurityServices.
     * @param hasEmbeddedResources are some META-INF/native-image/ files present.
     */
    public void complete(final String graalVersion, final boolean hasEmbeddedResources) {
        this.graalVersion = graalVersion;
        if (graalVersion != null && (graalVersion.startsWith("21.") || graalVersion.startsWith("20."))) {
            if (allowIncompleteClasspath == null) {
                allowIncompleteClasspath = !graalVersion.contains("-graal"); // 21*graal* needs it false, previous 21 needs it true :facepalm:
            }
            if (enableAllSecurityServices == null) {
                enableAllSecurityServices = !graalVersion.contains("-graal"); // 21*graal* needs it false, previous 21 needs it true :facepalm:
            }
        } else {
            // /!\ we disable this flag since recent graalvm versions don't need it anymore
            allowIncompleteClasspath = false;
            enableAllSecurityServices = false;
        }
        if (!Boolean.getBoolean("arthur.unlockexperimentalvmoptions.skip") && (customOptions == null || (!customOptions.contains("-H:+UnlockExperimentalVMOptions") && !customOptions.contains("-H:-UnlockExperimentalVMOptions"))) && hasEmbeddedResources) {
            if (customOptions == null) {
                customOptions = new ArrayList<>();
            }
            customOptions.add("-H:+UnlockExperimentalVMOptions");
        }
    }

    /**
     * @param graalVersion the graalvm version used to complete this configuration, in particular allowIncompleteClasspath and enableAllSecurityServices.
     * @deprecated use {@link #complete(String, boolean)}.
     */
    @Deprecated
    public void complete(final String graalVersion) {
        complete(graalVersion, false);
    }

    /**
     * Minimum GraalVM major version that enables the modern combined {@code reachability-metadata.json}
     * emission (glob resources, {@code type}-based reflection, {@code reflection}/{@code jni} split).
     */
    public static final int REACHABILITY_METADATA_MIN_MAJOR = 25;

    /**
     * Whether arthur should emit the modern combined {@code reachability-metadata.json} instead of
     * the legacy per-type {@code -H:*ConfigurationFiles}.
     * <p>
     * On by default for GraalVM major &ge; {@link #REACHABILITY_METADATA_MIN_MAJOR}; overridable with
     * the {@code arthur.reachabilityMetadata.version} system property (e.g. {@code 24} to enable from
     * GraalVM 24, {@code 0} to force the legacy path).
     *
     * @return {@code true} to emit the modern combined metadata.
     */
    public boolean reachabilityMetadataEnabled() {
        return modernMetadataMajor() >= REACHABILITY_METADATA_MIN_MAJOR;
    }

    /**
     * @return the GraalVM major version used for the metadata switch decision (system-property
     * override first, then the resolved {@link #getGraalVersion() graalVersion}; assumes modern when
     * unknown).
     */
    public int modernMetadataMajor() {
        final String override = System.getProperty("arthur.reachabilityMetadata.version");
        if (override != null) {
            return Integer.parseInt(override.trim());
        }
        final String version = graalVersion;
        if (version == null || version.isEmpty()) {
            return 0; // unknown GraalVM -> keep the legacy path (arthur's historical default)
        }
        final int firstDot = version.indexOf('.');
        final String major = firstDot > 0 ? version.substring(0, firstDot) : version;
        try {
            return Integer.parseInt(major);
        } catch (final NumberFormatException e) {
            return REACHABILITY_METADATA_MIN_MAJOR;
        }
    }


    public enum FallbackMode {
        no, auto, force;
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

    public ArthurNativeImageConfiguration() {
    }

    public String getNativeImage() {
        return this.nativeImage;
    }

    public Collection<String> getCustomOptions() {
        return this.customOptions;
    }

    public Collection<String> getClasspath() {
        return this.classpath;
    }

    public Collection<String> getDynamicProxyConfigurationFiles() {
        return this.dynamicProxyConfigurationFiles;
    }

    public Collection<String> getReflectionConfigurationFiles() {
        return this.reflectionConfigurationFiles;
    }

    public Collection<String> getResourcesConfigurationFiles() {
        return this.resourcesConfigurationFiles;
    }

    public Collection<String> getIncludeResourceBundles() {
        return this.includeResourceBundles;
    }

    public Collection<String> getInitializeAtRunTime() {
        return this.initializeAtRunTime;
    }

    public Collection<String> getInitializeAtBuildTime() {
        return this.initializeAtBuildTime;
    }

    public int getMaxRuntimeCompileMethods() {
        return this.maxRuntimeCompileMethods;
    }

    public boolean isEnforceMaxRuntimeCompileMethods() {
        return this.enforceMaxRuntimeCompileMethods;
    }

    public boolean isAddAllCharsets() {
        return this.addAllCharsets;
    }

    public boolean isReportExceptionStackTraces() {
        return this.reportExceptionStackTraces;
    }

    public boolean isPrintClassInitialization() {
        return this.printClassInitialization;
    }

    public FallbackMode getFallbackMode() {
        return this.fallbackMode;
    }

    public boolean isBuildStaticImage() {
        return this.buildStaticImage;
    }

    @java.lang.Deprecated
    public Boolean getAllowIncompleteClasspath() {
        return this.allowIncompleteClasspath;
    }

    public boolean isReportUnsupportedElementsAtRuntime() {
        return this.reportUnsupportedElementsAtRuntime;
    }

    @java.lang.Deprecated
    public Boolean getEnableAllSecurityServices() {
        return this.enableAllSecurityServices;
    }

    public Boolean getNoServer() {
        return this.noServer;
    }

    /**
     * The GraalVM version arthur builds for (set by {@link #complete(String, boolean)}).
     * Used to select the metadata emission mode (combined {@code reachability-metadata.json}
     * on modern GraalVM vs. legacy per-type {@code -H:*ConfigurationFiles}).
     */
    public String getGraalVersion() {
        return this.graalVersion;
    }

    public String getMain() {
        return this.main;
    }

    public String getOutput() {
        return this.output;
    }

    public boolean isInheritIO() {
        return this.inheritIO;
    }

    public void setNativeImage(final String nativeImage) {
        this.nativeImage = nativeImage;
    }

    public void setCustomOptions(final Collection<String> customOptions) {
        this.customOptions = customOptions;
    }

    public void setClasspath(final Collection<String> classpath) {
        this.classpath = classpath;
    }

    public void setDynamicProxyConfigurationFiles(final Collection<String> dynamicProxyConfigurationFiles) {
        this.dynamicProxyConfigurationFiles = dynamicProxyConfigurationFiles;
    }

    public void setReflectionConfigurationFiles(final Collection<String> reflectionConfigurationFiles) {
        this.reflectionConfigurationFiles = reflectionConfigurationFiles;
    }

    public void setResourcesConfigurationFiles(final Collection<String> resourcesConfigurationFiles) {
        this.resourcesConfigurationFiles = resourcesConfigurationFiles;
    }

    public void setIncludeResourceBundles(final Collection<String> includeResourceBundles) {
        this.includeResourceBundles = includeResourceBundles;
    }

    public void setInitializeAtRunTime(final Collection<String> initializeAtRunTime) {
        this.initializeAtRunTime = initializeAtRunTime;
    }

    public void setInitializeAtBuildTime(final Collection<String> initializeAtBuildTime) {
        this.initializeAtBuildTime = initializeAtBuildTime;
    }

    public void setMaxRuntimeCompileMethods(final int maxRuntimeCompileMethods) {
        this.maxRuntimeCompileMethods = maxRuntimeCompileMethods;
    }

    public void setEnforceMaxRuntimeCompileMethods(final boolean enforceMaxRuntimeCompileMethods) {
        this.enforceMaxRuntimeCompileMethods = enforceMaxRuntimeCompileMethods;
    }

    public void setAddAllCharsets(final boolean addAllCharsets) {
        this.addAllCharsets = addAllCharsets;
    }

    public void setReportExceptionStackTraces(final boolean reportExceptionStackTraces) {
        this.reportExceptionStackTraces = reportExceptionStackTraces;
    }

    public void setPrintClassInitialization(final boolean printClassInitialization) {
        this.printClassInitialization = printClassInitialization;
    }

    public void setFallbackMode(final FallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    public void setBuildStaticImage(final boolean buildStaticImage) {
        this.buildStaticImage = buildStaticImage;
    }

    @java.lang.Deprecated
    public void setAllowIncompleteClasspath(final Boolean allowIncompleteClasspath) {
        this.allowIncompleteClasspath = allowIncompleteClasspath;
    }

    public void setReportUnsupportedElementsAtRuntime(final boolean reportUnsupportedElementsAtRuntime) {
        this.reportUnsupportedElementsAtRuntime = reportUnsupportedElementsAtRuntime;
    }

    @java.lang.Deprecated
    public void setEnableAllSecurityServices(final Boolean enableAllSecurityServices) {
        this.enableAllSecurityServices = enableAllSecurityServices;
    }

    public void setNoServer(final Boolean noServer) {
        this.noServer = noServer;
    }

    /**
     * The GraalVM version arthur builds for (set by {@link #complete(String, boolean)}).
     * Used to select the metadata emission mode (combined {@code reachability-metadata.json}
     * on modern GraalVM vs. legacy per-type {@code -H:*ConfigurationFiles}).
     */
    public void setGraalVersion(final String graalVersion) {
        this.graalVersion = graalVersion;
    }

    public void setMain(final String main) {
        this.main = main;
    }

    public void setOutput(final String output) {
        this.output = output;
    }

    public void setInheritIO(final boolean inheritIO) {
        this.inheritIO = inheritIO;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof ArthurNativeImageConfiguration)) return false;
        final ArthurNativeImageConfiguration other = (ArthurNativeImageConfiguration) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.getMaxRuntimeCompileMethods() != other.getMaxRuntimeCompileMethods()) return false;
        if (this.isEnforceMaxRuntimeCompileMethods() != other.isEnforceMaxRuntimeCompileMethods()) return false;
        if (this.isAddAllCharsets() != other.isAddAllCharsets()) return false;
        if (this.isReportExceptionStackTraces() != other.isReportExceptionStackTraces()) return false;
        if (this.isPrintClassInitialization() != other.isPrintClassInitialization()) return false;
        if (this.isBuildStaticImage() != other.isBuildStaticImage()) return false;
        if (this.isReportUnsupportedElementsAtRuntime() != other.isReportUnsupportedElementsAtRuntime()) return false;
        if (this.isInheritIO() != other.isInheritIO()) return false;
        final java.lang.Object this$allowIncompleteClasspath = this.getAllowIncompleteClasspath();
        final java.lang.Object other$allowIncompleteClasspath = other.getAllowIncompleteClasspath();
        if (this$allowIncompleteClasspath == null ? other$allowIncompleteClasspath != null : !this$allowIncompleteClasspath.equals(other$allowIncompleteClasspath)) return false;
        final java.lang.Object this$enableAllSecurityServices = this.getEnableAllSecurityServices();
        final java.lang.Object other$enableAllSecurityServices = other.getEnableAllSecurityServices();
        if (this$enableAllSecurityServices == null ? other$enableAllSecurityServices != null : !this$enableAllSecurityServices.equals(other$enableAllSecurityServices)) return false;
        final java.lang.Object this$noServer = this.getNoServer();
        final java.lang.Object other$noServer = other.getNoServer();
        if (this$noServer == null ? other$noServer != null : !this$noServer.equals(other$noServer)) return false;
        final java.lang.Object this$nativeImage = this.getNativeImage();
        final java.lang.Object other$nativeImage = other.getNativeImage();
        if (this$nativeImage == null ? other$nativeImage != null : !this$nativeImage.equals(other$nativeImage)) return false;
        final java.lang.Object this$customOptions = this.getCustomOptions();
        final java.lang.Object other$customOptions = other.getCustomOptions();
        if (this$customOptions == null ? other$customOptions != null : !this$customOptions.equals(other$customOptions)) return false;
        final java.lang.Object this$classpath = this.getClasspath();
        final java.lang.Object other$classpath = other.getClasspath();
        if (this$classpath == null ? other$classpath != null : !this$classpath.equals(other$classpath)) return false;
        final java.lang.Object this$dynamicProxyConfigurationFiles = this.getDynamicProxyConfigurationFiles();
        final java.lang.Object other$dynamicProxyConfigurationFiles = other.getDynamicProxyConfigurationFiles();
        if (this$dynamicProxyConfigurationFiles == null ? other$dynamicProxyConfigurationFiles != null : !this$dynamicProxyConfigurationFiles.equals(other$dynamicProxyConfigurationFiles)) return false;
        final java.lang.Object this$reflectionConfigurationFiles = this.getReflectionConfigurationFiles();
        final java.lang.Object other$reflectionConfigurationFiles = other.getReflectionConfigurationFiles();
        if (this$reflectionConfigurationFiles == null ? other$reflectionConfigurationFiles != null : !this$reflectionConfigurationFiles.equals(other$reflectionConfigurationFiles)) return false;
        final java.lang.Object this$resourcesConfigurationFiles = this.getResourcesConfigurationFiles();
        final java.lang.Object other$resourcesConfigurationFiles = other.getResourcesConfigurationFiles();
        if (this$resourcesConfigurationFiles == null ? other$resourcesConfigurationFiles != null : !this$resourcesConfigurationFiles.equals(other$resourcesConfigurationFiles)) return false;
        final java.lang.Object this$includeResourceBundles = this.getIncludeResourceBundles();
        final java.lang.Object other$includeResourceBundles = other.getIncludeResourceBundles();
        if (this$includeResourceBundles == null ? other$includeResourceBundles != null : !this$includeResourceBundles.equals(other$includeResourceBundles)) return false;
        final java.lang.Object this$initializeAtRunTime = this.getInitializeAtRunTime();
        final java.lang.Object other$initializeAtRunTime = other.getInitializeAtRunTime();
        if (this$initializeAtRunTime == null ? other$initializeAtRunTime != null : !this$initializeAtRunTime.equals(other$initializeAtRunTime)) return false;
        final java.lang.Object this$initializeAtBuildTime = this.getInitializeAtBuildTime();
        final java.lang.Object other$initializeAtBuildTime = other.getInitializeAtBuildTime();
        if (this$initializeAtBuildTime == null ? other$initializeAtBuildTime != null : !this$initializeAtBuildTime.equals(other$initializeAtBuildTime)) return false;
        final java.lang.Object this$fallbackMode = this.getFallbackMode();
        final java.lang.Object other$fallbackMode = other.getFallbackMode();
        if (this$fallbackMode == null ? other$fallbackMode != null : !this$fallbackMode.equals(other$fallbackMode)) return false;
        final java.lang.Object this$graalVersion = this.getGraalVersion();
        final java.lang.Object other$graalVersion = other.getGraalVersion();
        if (this$graalVersion == null ? other$graalVersion != null : !this$graalVersion.equals(other$graalVersion)) return false;
        final java.lang.Object this$main = this.getMain();
        final java.lang.Object other$main = other.getMain();
        if (this$main == null ? other$main != null : !this$main.equals(other$main)) return false;
        final java.lang.Object this$output = this.getOutput();
        final java.lang.Object other$output = other.getOutput();
        if (this$output == null ? other$output != null : !this$output.equals(other$output)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof ArthurNativeImageConfiguration;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getMaxRuntimeCompileMethods();
        result = result * PRIME + (this.isEnforceMaxRuntimeCompileMethods() ? 79 : 97);
        result = result * PRIME + (this.isAddAllCharsets() ? 79 : 97);
        result = result * PRIME + (this.isReportExceptionStackTraces() ? 79 : 97);
        result = result * PRIME + (this.isPrintClassInitialization() ? 79 : 97);
        result = result * PRIME + (this.isBuildStaticImage() ? 79 : 97);
        result = result * PRIME + (this.isReportUnsupportedElementsAtRuntime() ? 79 : 97);
        result = result * PRIME + (this.isInheritIO() ? 79 : 97);
        final java.lang.Object $allowIncompleteClasspath = this.getAllowIncompleteClasspath();
        result = result * PRIME + ($allowIncompleteClasspath == null ? 43 : $allowIncompleteClasspath.hashCode());
        final java.lang.Object $enableAllSecurityServices = this.getEnableAllSecurityServices();
        result = result * PRIME + ($enableAllSecurityServices == null ? 43 : $enableAllSecurityServices.hashCode());
        final java.lang.Object $noServer = this.getNoServer();
        result = result * PRIME + ($noServer == null ? 43 : $noServer.hashCode());
        final java.lang.Object $nativeImage = this.getNativeImage();
        result = result * PRIME + ($nativeImage == null ? 43 : $nativeImage.hashCode());
        final java.lang.Object $customOptions = this.getCustomOptions();
        result = result * PRIME + ($customOptions == null ? 43 : $customOptions.hashCode());
        final java.lang.Object $classpath = this.getClasspath();
        result = result * PRIME + ($classpath == null ? 43 : $classpath.hashCode());
        final java.lang.Object $dynamicProxyConfigurationFiles = this.getDynamicProxyConfigurationFiles();
        result = result * PRIME + ($dynamicProxyConfigurationFiles == null ? 43 : $dynamicProxyConfigurationFiles.hashCode());
        final java.lang.Object $reflectionConfigurationFiles = this.getReflectionConfigurationFiles();
        result = result * PRIME + ($reflectionConfigurationFiles == null ? 43 : $reflectionConfigurationFiles.hashCode());
        final java.lang.Object $resourcesConfigurationFiles = this.getResourcesConfigurationFiles();
        result = result * PRIME + ($resourcesConfigurationFiles == null ? 43 : $resourcesConfigurationFiles.hashCode());
        final java.lang.Object $includeResourceBundles = this.getIncludeResourceBundles();
        result = result * PRIME + ($includeResourceBundles == null ? 43 : $includeResourceBundles.hashCode());
        final java.lang.Object $initializeAtRunTime = this.getInitializeAtRunTime();
        result = result * PRIME + ($initializeAtRunTime == null ? 43 : $initializeAtRunTime.hashCode());
        final java.lang.Object $initializeAtBuildTime = this.getInitializeAtBuildTime();
        result = result * PRIME + ($initializeAtBuildTime == null ? 43 : $initializeAtBuildTime.hashCode());
        final java.lang.Object $fallbackMode = this.getFallbackMode();
        result = result * PRIME + ($fallbackMode == null ? 43 : $fallbackMode.hashCode());
        final java.lang.Object $graalVersion = this.getGraalVersion();
        result = result * PRIME + ($graalVersion == null ? 43 : $graalVersion.hashCode());
        final java.lang.Object $main = this.getMain();
        result = result * PRIME + ($main == null ? 43 : $main.hashCode());
        final java.lang.Object $output = this.getOutput();
        result = result * PRIME + ($output == null ? 43 : $output.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "ArthurNativeImageConfiguration(nativeImage=" + this.getNativeImage() + ", customOptions=" + this.getCustomOptions() + ", classpath=" + this.getClasspath() + ", dynamicProxyConfigurationFiles=" + this.getDynamicProxyConfigurationFiles() + ", reflectionConfigurationFiles=" + this.getReflectionConfigurationFiles() + ", resourcesConfigurationFiles=" + this.getResourcesConfigurationFiles() + ", includeResourceBundles=" + this.getIncludeResourceBundles() + ", initializeAtRunTime=" + this.getInitializeAtRunTime() + ", initializeAtBuildTime=" + this.getInitializeAtBuildTime() + ", maxRuntimeCompileMethods=" + this.getMaxRuntimeCompileMethods() + ", enforceMaxRuntimeCompileMethods=" + this.isEnforceMaxRuntimeCompileMethods() + ", addAllCharsets=" + this.isAddAllCharsets() + ", reportExceptionStackTraces=" + this.isReportExceptionStackTraces() + ", printClassInitialization=" + this.isPrintClassInitialization() + ", fallbackMode=" + this.getFallbackMode() + ", buildStaticImage=" + this.isBuildStaticImage() + ", allowIncompleteClasspath=" + this.getAllowIncompleteClasspath() + ", reportUnsupportedElementsAtRuntime=" + this.isReportUnsupportedElementsAtRuntime() + ", enableAllSecurityServices=" + this.getEnableAllSecurityServices() + ", noServer=" + this.getNoServer() + ", graalVersion=" + this.getGraalVersion() + ", main=" + this.getMain() + ", output=" + this.getOutput() + ", inheritIO=" + this.isInheritIO() + ")";
    }
}

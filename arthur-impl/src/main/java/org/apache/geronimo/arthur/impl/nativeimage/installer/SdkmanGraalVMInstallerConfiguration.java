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
package org.apache.geronimo.arthur.impl.nativeimage.installer;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SdkmanGraalVMInstallerConfiguration {
    private final boolean offline;
    private final boolean inheritIO;
    private final String url;
    private final String version;
    private final String platform;
    private final String gav;
    private final Path workdir;
    private final Function<String, Path> resolver;
    private final BiFunction<String, Path, Path> installer;
    private final BiConsumer<Path, Path> extractor;

    SdkmanGraalVMInstallerConfiguration(final boolean offline, final boolean inheritIO, final String url, final String version, final String platform, final String gav, final Path workdir, final Function<String, Path> resolver, final BiFunction<String, Path, Path> installer, final BiConsumer<Path, Path> extractor) {
        this.offline = offline;
        this.inheritIO = inheritIO;
        this.url = url;
        this.version = version;
        this.platform = platform;
        this.gav = gav;
        this.workdir = workdir;
        this.resolver = resolver;
        this.installer = installer;
        this.extractor = extractor;
    }


    public static class SdkmanGraalVMInstallerConfigurationBuilder {
        private boolean offline;
        private boolean inheritIO;
        private String url;
        private String version;
        private String platform;
        private String gav;
        private Path workdir;
        private Function<String, Path> resolver;
        private BiFunction<String, Path, Path> installer;
        private BiConsumer<Path, Path> extractor;

        SdkmanGraalVMInstallerConfigurationBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder offline(final boolean offline) {
            this.offline = offline;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder inheritIO(final boolean inheritIO) {
            this.inheritIO = inheritIO;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder url(final String url) {
            this.url = url;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder version(final String version) {
            this.version = version;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder platform(final String platform) {
            this.platform = platform;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder gav(final String gav) {
            this.gav = gav;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder workdir(final Path workdir) {
            this.workdir = workdir;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder resolver(final Function<String, Path> resolver) {
            this.resolver = resolver;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder installer(final BiFunction<String, Path, Path> installer) {
            this.installer = installer;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder extractor(final BiConsumer<Path, Path> extractor) {
            this.extractor = extractor;
            return this;
        }

        public SdkmanGraalVMInstallerConfiguration build() {
            return new SdkmanGraalVMInstallerConfiguration(this.offline, this.inheritIO, this.url, this.version, this.platform, this.gav, this.workdir, this.resolver, this.installer, this.extractor);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder(offline=" + this.offline + ", inheritIO=" + this.inheritIO + ", url=" + this.url + ", version=" + this.version + ", platform=" + this.platform + ", gav=" + this.gav + ", workdir=" + this.workdir + ", resolver=" + this.resolver + ", installer=" + this.installer + ", extractor=" + this.extractor + ")";
        }
    }

    public static SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder builder() {
        return new SdkmanGraalVMInstallerConfiguration.SdkmanGraalVMInstallerConfigurationBuilder();
    }

    public boolean isOffline() {
        return this.offline;
    }

    public boolean isInheritIO() {
        return this.inheritIO;
    }

    public String getUrl() {
        return this.url;
    }

    public String getVersion() {
        return this.version;
    }

    public String getPlatform() {
        return this.platform;
    }

    public String getGav() {
        return this.gav;
    }

    public Path getWorkdir() {
        return this.workdir;
    }

    public Function<String, Path> getResolver() {
        return this.resolver;
    }

    public BiFunction<String, Path, Path> getInstaller() {
        return this.installer;
    }

    public BiConsumer<Path, Path> getExtractor() {
        return this.extractor;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof SdkmanGraalVMInstallerConfiguration)) return false;
        final SdkmanGraalVMInstallerConfiguration other = (SdkmanGraalVMInstallerConfiguration) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.isOffline() != other.isOffline()) return false;
        if (this.isInheritIO() != other.isInheritIO()) return false;
        final java.lang.Object this$url = this.getUrl();
        final java.lang.Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) return false;
        final java.lang.Object this$version = this.getVersion();
        final java.lang.Object other$version = other.getVersion();
        if (this$version == null ? other$version != null : !this$version.equals(other$version)) return false;
        final java.lang.Object this$platform = this.getPlatform();
        final java.lang.Object other$platform = other.getPlatform();
        if (this$platform == null ? other$platform != null : !this$platform.equals(other$platform)) return false;
        final java.lang.Object this$gav = this.getGav();
        final java.lang.Object other$gav = other.getGav();
        if (this$gav == null ? other$gav != null : !this$gav.equals(other$gav)) return false;
        final java.lang.Object this$workdir = this.getWorkdir();
        final java.lang.Object other$workdir = other.getWorkdir();
        if (this$workdir == null ? other$workdir != null : !this$workdir.equals(other$workdir)) return false;
        final java.lang.Object this$resolver = this.getResolver();
        final java.lang.Object other$resolver = other.getResolver();
        if (this$resolver == null ? other$resolver != null : !this$resolver.equals(other$resolver)) return false;
        final java.lang.Object this$installer = this.getInstaller();
        final java.lang.Object other$installer = other.getInstaller();
        if (this$installer == null ? other$installer != null : !this$installer.equals(other$installer)) return false;
        final java.lang.Object this$extractor = this.getExtractor();
        final java.lang.Object other$extractor = other.getExtractor();
        if (this$extractor == null ? other$extractor != null : !this$extractor.equals(other$extractor)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof SdkmanGraalVMInstallerConfiguration;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isOffline() ? 79 : 97);
        result = result * PRIME + (this.isInheritIO() ? 79 : 97);
        final java.lang.Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final java.lang.Object $version = this.getVersion();
        result = result * PRIME + ($version == null ? 43 : $version.hashCode());
        final java.lang.Object $platform = this.getPlatform();
        result = result * PRIME + ($platform == null ? 43 : $platform.hashCode());
        final java.lang.Object $gav = this.getGav();
        result = result * PRIME + ($gav == null ? 43 : $gav.hashCode());
        final java.lang.Object $workdir = this.getWorkdir();
        result = result * PRIME + ($workdir == null ? 43 : $workdir.hashCode());
        final java.lang.Object $resolver = this.getResolver();
        result = result * PRIME + ($resolver == null ? 43 : $resolver.hashCode());
        final java.lang.Object $installer = this.getInstaller();
        result = result * PRIME + ($installer == null ? 43 : $installer.hashCode());
        final java.lang.Object $extractor = this.getExtractor();
        result = result * PRIME + ($extractor == null ? 43 : $extractor.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "SdkmanGraalVMInstallerConfiguration(offline=" + this.isOffline() + ", inheritIO=" + this.isInheritIO() + ", url=" + this.getUrl() + ", version=" + this.getVersion() + ", platform=" + this.getPlatform() + ", gav=" + this.getGav() + ", workdir=" + this.getWorkdir() + ", resolver=" + this.getResolver() + ", installer=" + this.getInstaller() + ", extractor=" + this.getExtractor() + ")";
    }
}

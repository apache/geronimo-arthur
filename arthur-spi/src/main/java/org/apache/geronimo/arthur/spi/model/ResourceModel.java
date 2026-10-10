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
package org.apache.geronimo.arthur.spi.model;

/**
 * Describes a resource to register into the native image.
 * <p>
 * GraalVM 21.x+ (and the combined {@code reachability-metadata.json} format used since GraalVM 25)
 * favours <strong>glob</strong> based resource descriptors
 * ({@code {"glob": "config/*.properties", "module": "optional.module.name"}}) over the legacy
 * <em>regex</em> based {@link #getPattern() pattern} descriptors.
 * <p>
 * For new code prefer {@link #getGlob() glob} and {@link #glob(String)}; the legacy
 * {@link #getPattern() pattern} regex descriptor is retained for backward compatibility and is
 * automatically migrated to a glob when targeting a modern GraalVM (see the arthur
 * {@code ConfigurationGenerator}).
 */
public class ResourceModel {
    /**
     * Modern GraalVM resource descriptor: a glob pattern matched against resource paths,
     * e.g. {@code "config/*.properties"} or {@code "assets/**"}. Prefer over the legacy
     * {@link #getPattern() pattern} when building for a modern GraalVM (combined metadata).
     */
    private String glob;

    /**
     * Optional GraalVM module descriptor the {@link #getGlob() glob} applies to.
     * Only meaningful when {@link #getGlob() glob} is set.
     */
    private String module;

    /**
     * Legacy GraalVM resource descriptor: a <em>regex</em> matched against resource paths.
     * <p>
     * Not GraalVM 25 friendly: with the combined {@code reachability-metadata.json} the resource
     * registration is glob-based. Retained for backward compatibility; arthur automatically converts
     * a non-null {@code pattern} to a {@link #getGlob() glob} when building for a modern GraalVM.
     *
     * @deprecated use {@link #getGlob() glob} instead (GraalVM 25/combined-metadata friendly).
     */
    @Deprecated
    private String pattern;

    public ResourceModel() {
    }

    /**
     * @param glob   the modern glob resource descriptor.
     * @param module the optional module name.
     * @param pattern the legacy (regex) resource pattern (may be null).
     */
    public ResourceModel(final String glob, final String module, final String pattern) {
        this.glob = glob;
        this.module = module;
        this.pattern = pattern;
    }

    /**
     * @param pattern the legacy (regex) resource pattern.
     * @deprecated use {@link #glob(String)} instead.
     */
    @Deprecated
    public ResourceModel(final String pattern) {
        this.pattern = pattern;
    }

    public String getGlob() {
        return glob;
    }

    public void setGlob(final String glob) {
        this.glob = glob;
    }

    public String getModule() {
        return module;
    }

    public void setModule(final String module) {
        this.module = module;
    }

    /**
     * @deprecated use {@link #getGlob() glob} instead.
     */
    @Deprecated
    public String getPattern() {
        return pattern;
    }

    /**
     * @deprecated use {@link #setGlob(String)} instead.
     */
    @Deprecated
    public void setPattern(final String pattern) {
        this.pattern = pattern;
    }

    /**
     * Creates a modern glob descriptor.
     * @param glob the glob resource descriptor.
     * @return the model configured with the glob.
     */
    public static ResourceModel glob(final String glob) {
        return new ResourceModel(glob, null, null);
    }

    /**
     * Creates a modern glob descriptor scoped to a module.
     * @param glob   the glob resource descriptor.
     * @param module the module name the glob applies to (may be null).
     * @return the model configured with the glob and module.
     */
    public static ResourceModel glob(final String glob, final String module) {
        return new ResourceModel(glob, module, null);
    }
}
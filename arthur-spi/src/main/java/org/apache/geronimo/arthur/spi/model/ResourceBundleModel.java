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

import java.util.Collection;

/**
 * Describes a resource bundle to register into the native image.
 * <p>
 * GraalVM resource bundles support the modern descriptor
 * {@code {"name": "pkg.Bundle", "locales": [...], "classNames": [...]}} since the combined
 * metadata format ({@code reachability-metadata.json}).
 */
public class ResourceBundleModel {
    /**
     * Fully qualified bundle basename, e.g. {@code "com.example.Messages"}.
     */
    private String name;

    /**
     * Optional locales to include for this bundle (a subset of the {@code -H:IncludeLocales}
     * / image locales).
     */
    private Collection<String> locales;

    /**
     * Optional fully qualified names of resource bundle classes to include directly, bypassing the
     * basename + locale lookup.
     */
    private Collection<String> classNames;

    public ResourceBundleModel() {
    }

    public ResourceBundleModel(final String name) {
        this.name = name;
    }

    public ResourceBundleModel(final String name, final Collection<String> locales, final Collection<String> classNames) {
        this.name = name;
        this.locales = locales;
        this.classNames = classNames;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Collection<String> getLocales() {
        return locales;
    }

    public void setLocales(final Collection<String> locales) {
        this.locales = locales;
    }

    public Collection<String> getClassNames() {
        return classNames;
    }

    public void setClassNames(final Collection<String> classNames) {
        this.classNames = classNames;
    }
}
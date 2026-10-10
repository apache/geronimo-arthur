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
 * Aggregates the resources (globs/regex patterns) and resource bundles registered for the native
 * image (legacy per-type {@code resources.arthur.json} emission, and the modern combined
 * {@code reachability-metadata.json} {@code resources} section).
 */
public class ResourcesModel {
    private Collection<ResourceModel> resources;
    private Collection<ResourceBundleModel> bundles;

    public ResourcesModel() {
    }

    public ResourcesModel(final Collection<ResourceModel> resources, final Collection<ResourceBundleModel> bundles) {
        this.resources = resources;
        this.bundles = bundles;
    }

    public Collection<ResourceModel> getResources() {
        return resources;
    }

    public void setResources(final Collection<ResourceModel> resources) {
        this.resources = resources;
    }

    public Collection<ResourceBundleModel> getBundles() {
        return bundles;
    }

    public void setBundles(final Collection<ResourceBundleModel> bundles) {
        this.bundles = bundles;
    }
}
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
package org.apache.geronimo.arthur.impl.nativeimage.generator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import lombok.Data;

@Data
public class DefautContext implements ArthurExtension.Context {
    private final ArthurNativeImageConfiguration configuration;
    private final Function<Class<? extends Annotation>, Collection<Class<?>>> finder;
    private final Collection<ClassReflectionModel> reflections = new HashSet<>();
    private final Collection<ResourceModel> resources = new HashSet<>();
    private final Collection<ResourceBundleModel> bundles = new HashSet<>();
    private final Collection<DynamicProxyModel> dynamicProxyModels = new HashSet<>();
    private boolean modified;

    @Override
    public <T extends Annotation> Collection<Class<?>> findAnnotatedClasses(final Class<T> annotation) {
        return finder.apply(annotation);
    }

    @Override
    public void register(final ClassReflectionModel classReflectionModel) {
        reflections.removeIf(it -> Objects.equals(classReflectionModel.getName(), it.getName()));
        reflections.add(classReflectionModel);
        modified = true;
    }

    @Override
    public void register(final ResourceModel resourceModel) {
        resources.add(resourceModel);
        modified = true;
    }

    @Override
    public void register(final ResourceBundleModel resourceBundleModel) {
        bundles.removeIf(it -> Objects.equals(it.getName(), resourceBundleModel.getName()));
        bundles.add(resourceBundleModel);
        modified = true;
    }

    @Override
    public void register(final DynamicProxyModel dynamicProxyModel) {
        if (dynamicProxyModels.add(dynamicProxyModel)) {
            modified = true;
        }
    }

    public void addReflectionConfigFile(final String path) {
        if (configuration.getReflectionConfigurationFiles() == null) {
            configuration.setReflectionConfigurationFiles(new ArrayList<>());
        }
        configuration.getReflectionConfigurationFiles().add(path);
    }

    public void addResourcesConfigFile(final String path) {
        if (configuration.getResourcesConfigurationFiles() == null) {
            configuration.setResourcesConfigurationFiles(new ArrayList<>());
        }
        configuration.getResourcesConfigurationFiles().add(path);
    }

    public void addDynamicProxiesConfigFile(final String path) {
        if (configuration.getDynamicProxyConfigurationFiles() == null) {
            configuration.setDynamicProxyConfigurationFiles(new ArrayList<>());
        }
        configuration.getDynamicProxyConfigurationFiles().add(path);
    }
}

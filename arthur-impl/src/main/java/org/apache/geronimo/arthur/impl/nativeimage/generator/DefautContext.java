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

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class DefautContext implements ArthurExtension.Context {
    private final ArthurNativeImageConfiguration configuration;
    private final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassesFinder;
    private final Function<Class<? extends Annotation>, Collection<Method>> methodFinder;
    private final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder;
    private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
    private final Collection<ClassReflectionModel> reflections = new HashSet<>();
    private final Collection<ResourceModel> resources = new HashSet<>();
    private final Collection<ResourceBundleModel> bundles = new HashSet<>();
    private final Collection<DynamicProxyModel> dynamicProxyModels = new HashSet<>();
    private final Map<String, String> extensionProperties;
    private final Map<String, byte[]> dynamicClasses = new HashMap<>();
    private boolean modified;

    @Override
    public <T extends Annotation> Collection<Class<?>> findAnnotatedClasses(final Class<T> annotation) {
        return annotatedClassesFinder.apply(annotation);
    }

    @Override
    public <T extends Annotation> Collection<Method> findAnnotatedMethods(final Class<T> annotation) {
        return methodFinder.apply(annotation);
    }

    @Override
    public <T extends Annotation> Collection<Field> findAnnotatedFields(final Class<T> annotation) {
        return fieldFinder.apply(annotation);
    }

    @Override
    public <T> Collection<Class<? extends T>> findImplementations(final Class<T> parent) {
        return Collection.class.cast(implementationFinder.apply(parent));
    }

    @Override
    public void registerGeneratedClass(final String name, final byte[] bytecode) {
        dynamicClasses.put(name, bytecode);
    }

    @Override
    public void register(final ClassReflectionModel classReflectionModel) {
        reflections.stream().filter(it -> Objects.equals(classReflectionModel.getName(), it.getName())).findFirst().map(it -> {
            it.merge(classReflectionModel);
            return it;
        }).orElseGet(() -> {
            reflections.add(classReflectionModel);
            return classReflectionModel;
        });
        modified = true;
    }

    @Override
    public void register(final ResourceModel resourceModel) {
        resources.add(resourceModel);
        modified = true;
    }

    @Override
    public void register(final ResourceBundleModel resourceBundleModel) {
        bundles.stream().filter(it -> Objects.equals(resourceBundleModel.getName(), it.getName())).findFirst().orElseGet(() -> {
            modified = true;
            bundles.add(resourceBundleModel);
            return resourceBundleModel;
        });
    }

    @Override
    public void register(final DynamicProxyModel dynamicProxyModel) {
        if (dynamicProxyModels.add(dynamicProxyModel)) {
            modified = true;
        }
    }

    @Override
    public void enableAllSecurityServices() {
        configuration.setEnableAllSecurityServices(true);
    }

    @Override
    public void enableAllCharsets() {
        configuration.setAddAllCharsets(true);
    }

    @Override
    public void includeResourceBundle(final String name) {
        if (configuration.getIncludeResourceBundles() == null) {
            configuration.setIncludeResourceBundles(new ArrayList<>());
        }
        configuration.getIncludeResourceBundles().add(name);
    }

    @Override
    public void initializeAtBuildTime(final String... classes) {
        if (configuration.getInitializeAtBuildTime() == null) {
            configuration.setInitializeAtBuildTime(new ArrayList<>());
        }
        configuration.getInitializeAtBuildTime().addAll(Stream.of(classes).filter(it -> !configuration.getInitializeAtBuildTime().contains(it)).collect(toList()));
    }

    @Override
    public void initializeAtRunTime(final String... classes) {
        if (configuration.getInitializeAtRunTime() == null) {
            configuration.setInitializeAtRunTime(new ArrayList<>());
        }
        configuration.getInitializeAtRunTime().addAll(Stream.of(classes).filter(it -> !configuration.getInitializeAtBuildTime().contains(it)).collect(toList()));
    }

    @Override
    public String getProperty(final String key) {
        return extensionProperties == null ? null : extensionProperties.get(key);
    }

    @Override
    public void setProperty(final String key, final String value) {
        extensionProperties.put(key, value);
    }

    @Override
    public void addNativeImageOption(final String option) {
        if (configuration.getCustomOptions() == null) {
            configuration.setCustomOptions(new ArrayList<>());
        }
        configuration.getCustomOptions().add(option);
    }

    @Override
    public Class<?> loadClass(final String name) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(name);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Stream<Class<?>> findHierarchy(final Class<?> clazz) {
        return doFindHierarchy(clazz, new HashSet<>());
    }

    @Override
    public Optional<Predicate<String>> createPredicate(final String property, final ArthurExtension.PredicateType type) {
        return ofNullable(getProperty(property)).flatMap(ex -> Stream.of(ex.split(",")).map(String::trim).filter(it -> !it.isEmpty()).map(it -> of((Predicate<String>) n -> type.test(it, n))).reduce(Optional.<Predicate<String>>empty(), (opt, p) -> opt.map(e -> of(e.or(p.orElseThrow(IllegalArgumentException::new)))).orElse(p)));
    }

    @Override
    public Predicate<String> createIncludesExcludes(final String propertyBase, final ArthurExtension.PredicateType type) {
        final Optional<Predicate<String>> includes = createPredicate(propertyBase + "includes", type);
        final Optional<Predicate<String>> excludes = createPredicate(propertyBase + "excludes", type);
        return n -> {
            final boolean hasInclude = includes.isPresent();
            if (hasInclude) {
                if (includes.orElseThrow(IllegalStateException::new).test(n)) {
                    return true;
                }
            }
            final boolean hasExclude = excludes.isPresent();
            if (hasExclude) {
                if (excludes.orElseThrow(IllegalStateException::new).test(n)) {
                    return false;
                }
            }
            if (hasExclude && !hasInclude) {
                return true;
            }
            return !hasExclude && !hasInclude;
        };
    }

    @Override
    public <T> T unwrap(final Class<T> type) {
        if (ArthurNativeImageConfiguration.class == type) {
            return type.cast(configuration);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Unsupported unwrapping: " + type);
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

    private Stream<Class<?>> doFindHierarchy(final Class<?> clazz, final Set<Class<?>> visited) {
        visited.add(clazz);
        return Stream.concat(Stream.concat(Stream.of(clazz), Stream.of(clazz.getSuperclass())), Stream.of(clazz.getInterfaces())).filter(it -> Object.class != it && it != null).flatMap(it -> visited.contains(it) ? Stream.of(it) : doFindHierarchy(it, visited)).distinct();
    }

    public DefautContext(final ArthurNativeImageConfiguration configuration, final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassesFinder, final Function<Class<? extends Annotation>, Collection<Method>> methodFinder, final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder, final Function<Class<?>, Collection<Class<?>>> implementationFinder, final Map<String, String> extensionProperties) {
        this.configuration = configuration;
        this.annotatedClassesFinder = annotatedClassesFinder;
        this.methodFinder = methodFinder;
        this.fieldFinder = fieldFinder;
        this.implementationFinder = implementationFinder;
        this.extensionProperties = extensionProperties;
    }

    public ArthurNativeImageConfiguration getConfiguration() {
        return this.configuration;
    }

    public Function<Class<? extends Annotation>, Collection<Class<?>>> getAnnotatedClassesFinder() {
        return this.annotatedClassesFinder;
    }

    public Function<Class<? extends Annotation>, Collection<Method>> getMethodFinder() {
        return this.methodFinder;
    }

    public Function<Class<? extends Annotation>, Collection<Field>> getFieldFinder() {
        return this.fieldFinder;
    }

    public Function<Class<?>, Collection<Class<?>>> getImplementationFinder() {
        return this.implementationFinder;
    }

    public Collection<ClassReflectionModel> getReflections() {
        return this.reflections;
    }

    public Collection<ResourceModel> getResources() {
        return this.resources;
    }

    public Collection<ResourceBundleModel> getBundles() {
        return this.bundles;
    }

    public Collection<DynamicProxyModel> getDynamicProxyModels() {
        return this.dynamicProxyModels;
    }

    public Map<String, String> getExtensionProperties() {
        return this.extensionProperties;
    }

    public Map<String, byte[]> getDynamicClasses() {
        return this.dynamicClasses;
    }

    public boolean isModified() {
        return this.modified;
    }

    public void setModified(final boolean modified) {
        this.modified = modified;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof DefautContext)) return false;
        final DefautContext other = (DefautContext) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.isModified() != other.isModified()) return false;
        final java.lang.Object this$configuration = this.getConfiguration();
        final java.lang.Object other$configuration = other.getConfiguration();
        if (this$configuration == null ? other$configuration != null : !this$configuration.equals(other$configuration)) return false;
        final java.lang.Object this$annotatedClassesFinder = this.getAnnotatedClassesFinder();
        final java.lang.Object other$annotatedClassesFinder = other.getAnnotatedClassesFinder();
        if (this$annotatedClassesFinder == null ? other$annotatedClassesFinder != null : !this$annotatedClassesFinder.equals(other$annotatedClassesFinder)) return false;
        final java.lang.Object this$methodFinder = this.getMethodFinder();
        final java.lang.Object other$methodFinder = other.getMethodFinder();
        if (this$methodFinder == null ? other$methodFinder != null : !this$methodFinder.equals(other$methodFinder)) return false;
        final java.lang.Object this$fieldFinder = this.getFieldFinder();
        final java.lang.Object other$fieldFinder = other.getFieldFinder();
        if (this$fieldFinder == null ? other$fieldFinder != null : !this$fieldFinder.equals(other$fieldFinder)) return false;
        final java.lang.Object this$implementationFinder = this.getImplementationFinder();
        final java.lang.Object other$implementationFinder = other.getImplementationFinder();
        if (this$implementationFinder == null ? other$implementationFinder != null : !this$implementationFinder.equals(other$implementationFinder)) return false;
        final java.lang.Object this$reflections = this.getReflections();
        final java.lang.Object other$reflections = other.getReflections();
        if (this$reflections == null ? other$reflections != null : !this$reflections.equals(other$reflections)) return false;
        final java.lang.Object this$resources = this.getResources();
        final java.lang.Object other$resources = other.getResources();
        if (this$resources == null ? other$resources != null : !this$resources.equals(other$resources)) return false;
        final java.lang.Object this$bundles = this.getBundles();
        final java.lang.Object other$bundles = other.getBundles();
        if (this$bundles == null ? other$bundles != null : !this$bundles.equals(other$bundles)) return false;
        final java.lang.Object this$dynamicProxyModels = this.getDynamicProxyModels();
        final java.lang.Object other$dynamicProxyModels = other.getDynamicProxyModels();
        if (this$dynamicProxyModels == null ? other$dynamicProxyModels != null : !this$dynamicProxyModels.equals(other$dynamicProxyModels)) return false;
        final java.lang.Object this$extensionProperties = this.getExtensionProperties();
        final java.lang.Object other$extensionProperties = other.getExtensionProperties();
        if (this$extensionProperties == null ? other$extensionProperties != null : !this$extensionProperties.equals(other$extensionProperties)) return false;
        final java.lang.Object this$dynamicClasses = this.getDynamicClasses();
        final java.lang.Object other$dynamicClasses = other.getDynamicClasses();
        if (this$dynamicClasses == null ? other$dynamicClasses != null : !this$dynamicClasses.equals(other$dynamicClasses)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof DefautContext;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isModified() ? 79 : 97);
        final java.lang.Object $configuration = this.getConfiguration();
        result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
        final java.lang.Object $annotatedClassesFinder = this.getAnnotatedClassesFinder();
        result = result * PRIME + ($annotatedClassesFinder == null ? 43 : $annotatedClassesFinder.hashCode());
        final java.lang.Object $methodFinder = this.getMethodFinder();
        result = result * PRIME + ($methodFinder == null ? 43 : $methodFinder.hashCode());
        final java.lang.Object $fieldFinder = this.getFieldFinder();
        result = result * PRIME + ($fieldFinder == null ? 43 : $fieldFinder.hashCode());
        final java.lang.Object $implementationFinder = this.getImplementationFinder();
        result = result * PRIME + ($implementationFinder == null ? 43 : $implementationFinder.hashCode());
        final java.lang.Object $reflections = this.getReflections();
        result = result * PRIME + ($reflections == null ? 43 : $reflections.hashCode());
        final java.lang.Object $resources = this.getResources();
        result = result * PRIME + ($resources == null ? 43 : $resources.hashCode());
        final java.lang.Object $bundles = this.getBundles();
        result = result * PRIME + ($bundles == null ? 43 : $bundles.hashCode());
        final java.lang.Object $dynamicProxyModels = this.getDynamicProxyModels();
        result = result * PRIME + ($dynamicProxyModels == null ? 43 : $dynamicProxyModels.hashCode());
        final java.lang.Object $extensionProperties = this.getExtensionProperties();
        result = result * PRIME + ($extensionProperties == null ? 43 : $extensionProperties.hashCode());
        final java.lang.Object $dynamicClasses = this.getDynamicClasses();
        result = result * PRIME + ($dynamicClasses == null ? 43 : $dynamicClasses.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "DefautContext(configuration=" + this.getConfiguration() + ", annotatedClassesFinder=" + this.getAnnotatedClassesFinder() + ", methodFinder=" + this.getMethodFinder() + ", fieldFinder=" + this.getFieldFinder() + ", implementationFinder=" + this.getImplementationFinder() + ", reflections=" + this.getReflections() + ", resources=" + this.getResources() + ", bundles=" + this.getBundles() + ", dynamicProxyModels=" + this.getDynamicProxyModels() + ", extensionProperties=" + this.getExtensionProperties() + ", dynamicClasses=" + this.getDynamicClasses() + ", modified=" + this.isModified() + ")";
    }
}

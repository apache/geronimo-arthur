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
package org.apache.geronimo.arthur.spi;

import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Enable to enrich the build with some metadata.
 */
public interface ArthurExtension {
    /**
     * @return the priority of this extension (natural order sorting).
     */
    default int order() {
        return 0;
    }

    /**
     * @param context current build context.
     */
    void execute(Context context);

    /**
     * Enables to mutate the native image command generation and to manipulate current "context" (classloader).
     */
    interface Context {
        /**
         * Enables to get a context instance with a callback per method call.
         * it is particularly useful when delegating to another extension some logic (encapsulating another extension).
         *
         * @param context the context to wrap.
         * @param handler the callback when a method is called on context.
         * @return the wrapped context.
         */
        Context wrap(Context context, InvocationHandler handler);

        /**
         * Find classes based on annotation on classes.
         *
         * @param annotation the marker annotation to look for.
         * @param <T>        the annotation type.
         * @return the list of classes with this annotation.
         */
        <T extends Annotation> Collection<Class<?>> findAnnotatedClasses(Class<T> annotation);

        /**
         * Simular to {@link #findAnnotatedClasses(Class)} but at method level.
         *
         * @param annotation the marker annotation to look for.
         * @param <T>        the annotation type.
         * @return the list of methods with this annotation.
         */
        <T extends Annotation> Collection<Method> findAnnotatedMethods(Class<T> annotation);

        /**
         * Simular to {@link #findAnnotatedMethods(Class)} but at field level.
         *
         * @param annotation the marker annotation to look for.
         * @param <T>        the annotation type.
         * @return the list of methods with this annotation.
         */
        <T extends Annotation> Collection<Field> findAnnotatedFields(Class<T> annotation);

        /**
         * Find subclasses and implementation of a parent.
         *
         * @param parent the parent class to use as marker.
         * @param <T>    the type of the parent.
         * @return the list of children classes.
         */
        <T> Collection<Class<? extends T>> findImplementations(Class<T> parent);

        /**
         * Adds a reflection model in the context, if it already exists it is replaced.
         *
         * @param classReflectionModel the instance to register.
         */
        void register(ClassReflectionModel classReflectionModel);

        /**
         * Adds a resource model in the context, if it already exists it is replaced.
         *
         * @param resourceModel the instance to register.
         */
        void register(ResourceModel resourceModel);

        /**
         * Adds a bundle model in the context, if it already exists it is replaced.
         *
         * @param resourceModel the instance to register.
         */
        void register(ResourceBundleModel resourceModel);

        /**
         * Adds a proxy model in the context, if it already exists it is replaced.
         *
         * @param dynamicProxyModel the instance to register.
         */
        void register(DynamicProxyModel dynamicProxyModel);

        /**
         * Enables java security in the native image.
         */
        void enableAllSecurityServices();

        /**
         * Includes charsets in the native image.
         */
        void enableAllCharsets();

        /**
         * Includes resource bundle (directly from the CLI since graal has some bugs on that as of today).
         */
        void includeResourceBundle(String name);

        /**
         * Forces classes to be initialized during the build and not at run time.
         *
         * @param classes classes to initialize.
         */
        void initializeAtBuildTime(String... classes);

        /**
         * Forces classes to be initialized during the run only, not the build.
         *
         * @param classes classes to initialize.
         */
        void initializeAtRunTime(String... classes);

        /**
         * Retrieve a context property, used to configured an extension.
         *
         * @param key the key to read.
         * @return the value or null if missing.
         */
        String getProperty(String key);

        /**
         * Sets a property in the context, it can be used if extensions are chained for example.
         *
         * @param key   the property key.
         * @param value the value to set for the specified key.
         */
        void setProperty(String key, String value);

        /**
         * Add specific native image option to the command line. Useful for custom graal extension options.
         *
         * @param option the command line option to set.
         */
        void addNativeImageOption(String option);

        /**
         * Loads a class in current context.
         *
         * @param name the class name to load.
         * @return the loaded class.
         * @throws IllegalStateException if the class can't be found.
         */
        Class<?> loadClass(String name);

        /**
         * Enable to add to native-image execution custom classes generated before the native-image execution.
         * It can be common for proxies and classes created at runtime normally.
         *
         * @param name     the class name.
         * @param bytecode the class bytecode.
         */
        void registerGeneratedClass(final String name, final byte[] bytecode);

        /**
         * Creates a stream of all classes (class, super classes and interfaces) for the specified class.
         *
         * @param from the class to look the hierarchy for.
         * @return the class hierarchy.
         */
        Stream<Class<?>> findHierarchy(Class<?> from);

        /**
         * Creates a predicate from the specified context property (key). It uses the value as a comma separated list.
         *
         * @param property the key to read the property from in the context.
         * @param type     matching type.
         * @return an optional predicate for the specified property.
         */
        Optional<Predicate<String>> createPredicate(String property, PredicateType type);

        /**
         * Use a base property suffixed with "includes" and "excludes" to create a matching predicate.
         * It relies on {@link #createPredicate(String, PredicateType)} and combine both in a single predicate.
         *
         * @param propertyBase the prefix to use to read predicate properties.
         * @param type         the type of matching.
         * @return the predicate for that properties pair.
         */
        Predicate<String> createIncludesExcludes(String propertyBase, PredicateType type);

        /**
         * Tries to unwrap current context in another type.
         *
         * @param type type to extract.
         * @param <T>  type to extract.
         * @return the extracted instance.
         */
        <T> T unwrap(Class<T> type);
    }

    enum PredicateType implements BiPredicate<String, String> {
        EQUALS {
            @Override
            public boolean test(final String value, final String item) {
                return Objects.equals(value, item);
            }
        },
        STARTS_WITH {
            @Override
            public boolean test(final String value, final String item) {
                return item != null && item.startsWith(value);
            }
        },
        MATCHES {
            @Override
            public boolean test(final String value, final String item) {
                return item != null && item.matches(value);
            }
        }
    }
}

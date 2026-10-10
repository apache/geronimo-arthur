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

import org.apache.geronimo.arthur.impl.nativeimage.generator.ConfigurationGenerator;
import org.apache.geronimo.arthur.impl.nativeimage.graal.CommandGenerator;
import org.apache.geronimo.arthur.impl.nativeimage.process.ProcessExecutor;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Function;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

public class ArthurNativeImageExecutor implements Runnable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ArthurNativeImageExecutor.class);
    private final ExecutorConfiguration configuration;

    @Override
    public void run() {
        final ConfigurationGenerator configurationGenerator = new ConfigurationGenerator(loadExtensions(), configuration.configuration, configuration.workingDirectory, configuration.jsonSerializer, configuration.annotatedClassFinder, configuration.annotatedFieldFinder, configuration.annotatedMethodFinder, configuration.implementationFinder, configuration.extensionProperties);
        configurationGenerator.run();
        final List<String> command = new CommandGenerator().generate(configuration.configuration);
        new ProcessExecutor(configuration.configuration.isInheritIO(), command, emptyMap()).run();
    }

    protected Iterable<ArthurExtension> loadExtensions() {
        return ServiceLoader.load(ArthurExtension.class, ofNullable(Thread.currentThread().getContextClassLoader()).orElseGet(ClassLoader::getSystemClassLoader));
    }


    public static class ExecutorConfiguration {
        private final BiConsumer<Object, Writer> jsonSerializer;
        private final Path workingDirectory;
        private final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassFinder;
        private final Function<Class<? extends Annotation>, Collection<Method>> annotatedMethodFinder;
        private final Function<Class<? extends Annotation>, Collection<Field>> annotatedFieldFinder;
        private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
        private final ArthurNativeImageConfiguration configuration;
        private final Map<String, String> extensionProperties;

        ExecutorConfiguration(final BiConsumer<Object, Writer> jsonSerializer, final Path workingDirectory, final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassFinder, final Function<Class<? extends Annotation>, Collection<Method>> annotatedMethodFinder, final Function<Class<? extends Annotation>, Collection<Field>> annotatedFieldFinder, final Function<Class<?>, Collection<Class<?>>> implementationFinder, final ArthurNativeImageConfiguration configuration, final Map<String, String> extensionProperties) {
            this.jsonSerializer = jsonSerializer;
            this.workingDirectory = workingDirectory;
            this.annotatedClassFinder = annotatedClassFinder;
            this.annotatedMethodFinder = annotatedMethodFinder;
            this.annotatedFieldFinder = annotatedFieldFinder;
            this.implementationFinder = implementationFinder;
            this.configuration = configuration;
            this.extensionProperties = extensionProperties;
        }


        public static class ExecutorConfigurationBuilder {
            private BiConsumer<Object, Writer> jsonSerializer;
            private Path workingDirectory;
            private Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassFinder;
            private Function<Class<? extends Annotation>, Collection<Method>> annotatedMethodFinder;
            private Function<Class<? extends Annotation>, Collection<Field>> annotatedFieldFinder;
            private Function<Class<?>, Collection<Class<?>>> implementationFinder;
            private ArthurNativeImageConfiguration configuration;
            private Map<String, String> extensionProperties;

            ExecutorConfigurationBuilder() {
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder jsonSerializer(final BiConsumer<Object, Writer> jsonSerializer) {
                this.jsonSerializer = jsonSerializer;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder workingDirectory(final Path workingDirectory) {
                this.workingDirectory = workingDirectory;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder annotatedClassFinder(final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassFinder) {
                this.annotatedClassFinder = annotatedClassFinder;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder annotatedMethodFinder(final Function<Class<? extends Annotation>, Collection<Method>> annotatedMethodFinder) {
                this.annotatedMethodFinder = annotatedMethodFinder;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder annotatedFieldFinder(final Function<Class<? extends Annotation>, Collection<Field>> annotatedFieldFinder) {
                this.annotatedFieldFinder = annotatedFieldFinder;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder implementationFinder(final Function<Class<?>, Collection<Class<?>>> implementationFinder) {
                this.implementationFinder = implementationFinder;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder configuration(final ArthurNativeImageConfiguration configuration) {
                this.configuration = configuration;
                return this;
            }

            /**
             * @return {@code this}.
             */
            public ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder extensionProperties(final Map<String, String> extensionProperties) {
                this.extensionProperties = extensionProperties;
                return this;
            }

            public ArthurNativeImageExecutor.ExecutorConfiguration build() {
                return new ArthurNativeImageExecutor.ExecutorConfiguration(this.jsonSerializer, this.workingDirectory, this.annotatedClassFinder, this.annotatedMethodFinder, this.annotatedFieldFinder, this.implementationFinder, this.configuration, this.extensionProperties);
            }

            @java.lang.Override
            public java.lang.String toString() {
                return "ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder(jsonSerializer=" + this.jsonSerializer + ", workingDirectory=" + this.workingDirectory + ", annotatedClassFinder=" + this.annotatedClassFinder + ", annotatedMethodFinder=" + this.annotatedMethodFinder + ", annotatedFieldFinder=" + this.annotatedFieldFinder + ", implementationFinder=" + this.implementationFinder + ", configuration=" + this.configuration + ", extensionProperties=" + this.extensionProperties + ")";
            }
        }

        public static ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder builder() {
            return new ArthurNativeImageExecutor.ExecutorConfiguration.ExecutorConfigurationBuilder();
        }
    }

    public ArthurNativeImageExecutor(final ExecutorConfiguration configuration) {
        this.configuration = configuration;
    }
}

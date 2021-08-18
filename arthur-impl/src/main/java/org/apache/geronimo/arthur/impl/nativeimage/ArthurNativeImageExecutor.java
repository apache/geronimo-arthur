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

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RequiredArgsConstructor
public class ArthurNativeImageExecutor implements Runnable {
    private final ExecutorConfiguration configuration;

    @Override
    public void run() {
        final ConfigurationGenerator configurationGenerator = new ConfigurationGenerator(
                loadExtensions(),
                configuration.configuration, configuration.workingDirectory, configuration.jsonSerializer,
                configuration.annotatedClassFinder, configuration.annotatedFieldFinder,
                configuration.annotatedMethodFinder, configuration.implementationFinder,
                configuration.extensionProperties);
        configurationGenerator.run();

        final List<String> command = new CommandGenerator().generate(configuration.configuration);
        new ProcessExecutor(
                configuration.configuration.isInheritIO(),
                command,
                emptyMap())
                .run();
    }

    protected Iterable<ArthurExtension> loadExtensions() {
        return ServiceLoader.load(
                ArthurExtension.class,
                ofNullable(Thread.currentThread().getContextClassLoader())
                        .orElseGet(ClassLoader::getSystemClassLoader));
    }

    @Builder
    public static class ExecutorConfiguration {
        private final BiConsumer<Object, Writer> jsonSerializer;
        private final Path workingDirectory;
        private final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassFinder;
        private final Function<Class<? extends Annotation>, Collection<Method>> annotatedMethodFinder;
        private final Function<Class<? extends Annotation>, Collection<Field>> annotatedFieldFinder;
        private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
        private final ArthurNativeImageConfiguration configuration;
        private final Map<String, String> extensionProperties;
    }
}

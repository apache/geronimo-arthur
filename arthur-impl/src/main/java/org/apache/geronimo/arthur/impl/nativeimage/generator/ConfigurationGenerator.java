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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourcesModel;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class ConfigurationGenerator implements Runnable {
    private final List<ArthurExtension> extensions;
    private final ArthurNativeImageConfiguration configuration;
    private final Path workingDirectory;
    private final BiConsumer<Object, Writer> jsonSerializer;
    private final Function<Class<? extends Annotation>, Collection<Class<?>>> classFinder;
    private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
    private final Function<Class<? extends Annotation>, Collection<Method>> methodFinder;
    private final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder;
    private final Map<String, String> extensionProperties;

    public ConfigurationGenerator(final Iterable<ArthurExtension> extensions, final ArthurNativeImageConfiguration configuration,
                                  final Path workingDirectory, final BiConsumer<Object, Writer> jsonSerializer,
                                  final Function<Class<? extends Annotation>, Collection<Class<?>>> classFinder,
                                  final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder,
                                  final Function<Class<? extends Annotation>, Collection<Method>> methodFinder,
                                  final Function<Class<?>, Collection<Class<?>>> implementationFinder,
                                  final Map<String, String> extensionProperties) {
        this.extensions = StreamSupport.stream(extensions.spliterator(), false).collect(toList());
        this.configuration = configuration;
        this.workingDirectory = workingDirectory;
        this.jsonSerializer = jsonSerializer;
        this.classFinder = classFinder;
        this.fieldFinder = fieldFinder;
        this.methodFinder = methodFinder;
        this.implementationFinder = implementationFinder;
        this.extensionProperties = extensionProperties;
    }

    @Override
    public void run() {
        // ensure to have a writtable instance (see Context#setProperty(String, String))
        final HashMap<String, String> properties = ofNullable(this.extensionProperties).map(HashMap::new).orElseGet(HashMap::new);
        properties.put("workingDirectory", workingDirectory.toAbsolutePath().toString());

        final DefautContext context = new DefautContext(configuration, classFinder, methodFinder, fieldFinder, implementationFinder, properties);
        for (final ArthurExtension extension : extensions) {
            log.debug("Executing {}", extension);
            context.setModified(false);
            extension.execute(context);
            if (context.isModified()) { // todo: loop while it modifies the context?
                log.info("Extension {} updated build context", extension.getClass().getName());
            }
        }
        try {
            updateConfiguration(context);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void updateConfiguration(final DefautContext context) throws IOException {
        if (!context.getReflections().isEmpty()) {
            ensureWorkingDirectoryExists();
            final Path json = workingDirectory.resolve("reflection.arthur.json");
            log.info("Creating reflection model '{}'", json);
            try (final Writer writer = Files.newBufferedWriter(
                    json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(
                        context.getReflections().stream()
                                .collect(groupingBy(ClassReflectionModel::getName))
                                .values().stream()
                                .map(this::merge)
                                .sorted(comparing(ClassReflectionModel::getName))
                                .collect(toList()),
                        writer);
            }
            context.addReflectionConfigFile(json.toAbsolutePath().toString());
        }
        if (!context.getResources().isEmpty() || !context.getBundles().isEmpty()) {
            final ResourcesModel resourcesModel = new ResourcesModel();
            if (!context.getResources().isEmpty()) {
                resourcesModel.setResources(context.getResources());
            }
            if (!context.getBundles().isEmpty()) {
                resourcesModel.setBundles(context.getBundles());
            }
            ensureWorkingDirectoryExists();
            final Path json = workingDirectory.resolve("resources.arthur.json");
            log.info("Creating resources model '{}'", json);
            try (final Writer writer = Files.newBufferedWriter(
                    json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(resourcesModel, writer);
            }
            context.addResourcesConfigFile(json.toAbsolutePath().toString());
        }
        if (!context.getDynamicProxyModels().isEmpty()) {
            final Set<Collection<String>> proxies = context.getDynamicProxyModels().stream()
                    .map(DynamicProxyModel::getClasses)
                    .collect(toCollection(LinkedHashSet::new));

            ensureWorkingDirectoryExists();
            final Path json = workingDirectory.resolve("dynamicproxies.arthur.json");
            log.info("Creating dynamic proxy model '{}'", json);
            try (final Writer writer = Files.newBufferedWriter(
                    json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(proxies, writer);
            }
            context.addDynamicProxiesConfigFile(json.toAbsolutePath().toString());
        }
        if (!context.getDynamicClasses().isEmpty()) {
            final Path dynamicClassesDir = workingDirectory.resolve("dynamic_classes");
            context.getDynamicClasses().forEach((name, content) -> {
                final Path target = dynamicClassesDir.resolve(name.replace('.', '/') + ".class");
                if (!Files.exists(target.getParent())) {
                    try {
                        Files.createDirectories(target.getParent());
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                try {
                    Files.write(target, content, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            log.info("Dumped {} generated classes in '{}'", context.getDynamicClasses().size(), dynamicClassesDir);
            if (configuration.getClasspath() == null) {
                configuration.setClasspath(singletonList(dynamicClassesDir.toString()));
            } else {
                configuration.setClasspath(Stream.concat(
                        configuration.getClasspath().stream(),
                        Stream.of(dynamicClassesDir.toString())
                ).distinct().collect(toList()));
            }
        }
    }

    private ClassReflectionModel merge(final List<ClassReflectionModel> classReflectionModels) {
        final Iterator<ClassReflectionModel> modelIterator = classReflectionModels.iterator();
        final ClassReflectionModel model = modelIterator.next();
        while (modelIterator.hasNext()) {
            model.merge(modelIterator.next());
        }
        return model;
    }

    private void ensureWorkingDirectoryExists() throws IOException {
        if (!Files.exists(workingDirectory)) {
            Files.createDirectories(workingDirectory);
        }
    }
}

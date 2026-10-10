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

public class ConfigurationGenerator implements Runnable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfigurationGenerator.class);
    private final List<ArthurExtension> extensions;
    private final ArthurNativeImageConfiguration configuration;
    private final Path workingDirectory;
    private final BiConsumer<Object, Writer> jsonSerializer;
    private final Function<Class<? extends Annotation>, Collection<Class<?>>> classFinder;
    private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
    private final Function<Class<? extends Annotation>, Collection<Method>> methodFinder;
    private final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder;
    private final Map<String, String> extensionProperties;

    public ConfigurationGenerator(final Iterable<ArthurExtension> extensions, final ArthurNativeImageConfiguration configuration, final Path workingDirectory, final BiConsumer<Object, Writer> jsonSerializer, final Function<Class<? extends Annotation>, Collection<Class<?>>> classFinder, final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder, final Function<Class<? extends Annotation>, Collection<Method>> methodFinder, final Function<Class<?>, Collection<Class<?>>> implementationFinder, final Map<String, String> extensionProperties) {
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
            if (context.isModified()) {
                // todo: loop while it modifies the context?
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
        if (configuration.reachabilityMetadataEnabled()) {
            // Modern GraalVM: emit a single combined reachability-metadata.json (glob resources,
            // type-based reflection, reflection/jni split) that GraalVM 25 parses with the modern
            // *MetadataParser paths. Legacy pattern regexes are migrated to globs on the fly.
            updateReachabilityMetadata(context);
            return;
        }
        if (!context.getReflections().isEmpty()) {
            ensureWorkingDirectoryExists();
            final Path json = workingDirectory.resolve("reflection.arthur.json");
            log.info("Creating reflection model \'{}\'", json);
            try (Writer writer = Files.newBufferedWriter(json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(context.getReflections().stream().collect(groupingBy(ClassReflectionModel::getName)).values().stream().map(this::merge).sorted(comparing(ClassReflectionModel::getName)).collect(toList()), writer);
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
            log.info("Creating resources model \'{}\'", json);
            try (Writer writer = Files.newBufferedWriter(json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(resourcesModel, writer);
            }
            context.addResourcesConfigFile(json.toAbsolutePath().toString());
        }
        if (!context.getDynamicProxyModels().isEmpty()) {
            final Set<Collection<String>> proxies = context.getDynamicProxyModels().stream().map(DynamicProxyModel::getClasses).collect(toCollection(LinkedHashSet::new));
            ensureWorkingDirectoryExists();
            final Path json = workingDirectory.resolve("dynamicproxies.arthur.json");
            log.info("Creating dynamic proxy model \'{}\'", json);
            try (Writer writer = Files.newBufferedWriter(json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
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
            log.info("Dumped {} generated classes in \'{}\'", context.getDynamicClasses().size(), dynamicClassesDir);
            if (configuration.getClasspath() == null) {
                configuration.setClasspath(singletonList(dynamicClassesDir.toString()));
            } else {
                configuration.setClasspath(Stream.concat(configuration.getClasspath().stream(), Stream.of(dynamicClassesDir.toString())).distinct().collect(toList()));
            }
        }
    }

    /**
     * Modern GraalVM (>= REACHABILITY_METADATA_MIN_MAJOR) emission: builds a single combined
     * {@code reachability-metadata.json} under {@code META-INF/native-image/<gid>/} so GraalVM
     * auto-discovers it from the classpath and parses it with the modern {@code *MetadataParser}s.
     * <p>
     * Legacy regex resource {@code pattern}s are migrated to {@code glob}s on the fly; bundles are
     * emitted as modern descriptors; reflection uses the {@code type} (and {@code jni}) sections.
     */
    private void updateReachabilityMetadata(final DefautContext context) throws IOException {
        ensureWorkingDirectoryExists();
        final Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (!context.getReflections().isEmpty()) {
            metadata.put("reflection", context.getReflections().stream().collect(groupingBy(ClassReflectionModel::className)).values().stream().map(this::merge).map(ClassReflectionModel::toModernEntry).collect(toList()));
        }
        final List<Object> resources = new java.util.ArrayList<>();
        for (final ClassReflectionModel reflection : context.getReflections()) {
            final Object jniEntry = reflection.toJniEntry();
            if (jniEntry != null) {
                resources.add(jniEntry);
            }
        }
        // resources: globs (with on-the-fly pattern->glob migration) ...
        for (final org.apache.geronimo.arthur.spi.model.ResourceModel resource : context.getResources()) {
            final Map<String, Object> glob = new java.util.LinkedHashMap<>();
            final String globValue = resource.getGlob() != null ? resource.getGlob() : migratePatternToGlob(resource.getPattern());
            if (globValue != null) {
                glob.put("glob", globValue);
                if (resource.getModule() != null) {
                    glob.put("module", resource.getModule());
                }
                resources.add(glob);
            }
        }
        // ... and bundles as modern descriptors
        for (final org.apache.geronimo.arthur.spi.model.ResourceBundleModel bundle : context.getBundles()) {
            final Map<String, Object> bundleEntry = new java.util.LinkedHashMap<>();
            bundleEntry.put("bundle", bundle.getName());
            if (bundle.getLocales() != null && !bundle.getLocales().isEmpty()) {
                bundleEntry.put("locales", bundle.getLocales());
            }
            if (bundle.getClassNames() != null && !bundle.getClassNames().isEmpty()) {
                bundleEntry.put("classNames", bundle.getClassNames());
            }
            resources.add(bundleEntry);
        }
        if (!resources.isEmpty()) {
            metadata.put("resources", resources);
        }
        final Path metadataDir = workingDirectory.resolve("META-INF/native-image/org.apache.geronimo.arthur");
        Files.createDirectories(metadataDir);
        final Path json = metadataDir.resolve("reachability-metadata.json");
        log.info("Creating combined reachability metadata model \'{}\'", json);
        try (Writer writer = Files.newBufferedWriter(json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            jsonSerializer.accept(metadata, writer);
        }
        // register the directory on the native-image classpath so GraalVM auto-discovers the file
        if (configuration.getClasspath() == null) {
            configuration.setClasspath(new java.util.ArrayList<>(singletonList(workingDirectory.toString())));
        } else if (!configuration.getClasspath().contains(workingDirectory.toString())) {
            configuration.setClasspath(Stream.concat(configuration.getClasspath().stream(), Stream.of(workingDirectory.toString())).collect(toList()));
        }
        // dynamic proxies stay a separate config (GraalVM keeps them out of the combined file)
        if (!context.getDynamicProxyModels().isEmpty()) {
            final Set<Collection<String>> proxies = context.getDynamicProxyModels().stream().map(DynamicProxyModel::getClasses).collect(toCollection(LinkedHashSet::new));
            final Path proxyFile = workingDirectory.resolve("dynamicproxies.arthur.json");
            try (Writer writer = Files.newBufferedWriter(proxyFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                jsonSerializer.accept(proxies, writer);
            }
            context.addDynamicProxiesConfigFile(proxyFile.toAbsolutePath().toString());
        }
        // generated dynamic classes are emitted by the caller (shared with the legacy path)
        dumpDynamicClasses(context);
    }

    private void dumpDynamicClasses(final DefautContext context) {
        if (context.getDynamicClasses().isEmpty()) {
            return;
        }
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
        log.info("Dumped {} generated classes in \'{}\'", context.getDynamicClasses().size(), dynamicClassesDir);
        if (configuration.getClasspath() == null) {
            configuration.setClasspath(singletonList(dynamicClassesDir.toString()));
        } else {
            configuration.setClasspath(Stream.concat(configuration.getClasspath().stream(), Stream.of(dynamicClassesDir.toString())).distinct().collect(toList()));
        }
    }

    /**
     * REQ 1 - on-the-fly migration of a legacy regex {@code pattern} to a GraalVM glob.
     * Glob-clean patterns are kept as-is (regex escapes are unescaped); common regex constructs
     * ({@code .*}, {@code \.}, etc.) are translated. Patterns that make no sense as a glob are
     * returned as a literal single-segment-safe glob (fallback).
     *
     * @param pattern the legacy regex resource pattern (may be null).
     * @return the equivalent glob, or {@code null} if {@code pattern} was null.
     */
    private String migratePatternToGlob(final String pattern) {
        if (pattern == null) {
            return null;
        }
        String glob =  // strip any literal quoting
        pattern.replace("\\Q", "").replace("\\Ez", "").replace("\\E", "").replace("\\.", ".").replace("\\/", "/").replace("\\\\", "\\");
        // translate common regex quantifiers/wildcards to glob wildcards
        glob = glob.replace(".*", "**");
        if (glob.indexOf('*') == -1 && glob.indexOf('?') == -1 && glob.indexOf('[') >= 0) {
            // regex character class - keep only if simple, else fall back to a literal
            glob = glob.replaceAll("\\[[^\\]]*\\]", "*");
        }
        return glob;
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

    public ConfigurationGenerator(final List<ArthurExtension> extensions, final ArthurNativeImageConfiguration configuration, final Path workingDirectory, final BiConsumer<Object, Writer> jsonSerializer, final Function<Class<? extends Annotation>, Collection<Class<?>>> classFinder, final Function<Class<?>, Collection<Class<?>>> implementationFinder, final Function<Class<? extends Annotation>, Collection<Method>> methodFinder, final Function<Class<? extends Annotation>, Collection<Field>> fieldFinder, final Map<String, String> extensionProperties) {
        this.extensions = extensions;
        this.configuration = configuration;
        this.workingDirectory = workingDirectory;
        this.jsonSerializer = jsonSerializer;
        this.classFinder = classFinder;
        this.implementationFinder = implementationFinder;
        this.methodFinder = methodFinder;
        this.fieldFinder = fieldFinder;
        this.extensionProperties = extensionProperties;
    }
}

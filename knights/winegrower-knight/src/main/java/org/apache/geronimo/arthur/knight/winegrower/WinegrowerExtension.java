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
package org.apache.geronimo.arthur.knight.winegrower;

import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.apache.xbean.finder.ClassLoaders.findUrls;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.winegrower.extension.build.common.MetadataBuilder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.util.Files;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

@Slf4j // todo: handle manifest.mf generation (replace bundle extension?)
public class WinegrowerExtension implements ArthurExtension {
    private DocumentBuilderFactory documentBuilderFactory;
    private XPath xpath;

    @Override
    public void execute(final Context context) {
        final Predicate<String> userFiler = context.createIncludesExcludes("extension.winegrower.", PredicateType.STARTS_WITH);
        final boolean generateMetadata = Boolean.parseBoolean(ofNullable(context.getProperty("extension.winegrower.metadata.generate")).orElse("true"));
        final boolean metadataAutoFiltering = Boolean.parseBoolean(ofNullable(context.getProperty("extension.winegrower.metadata.autoFiltering")).orElse("true"));
        final String metadataDefaultJarName = context.getProperty("extension.winegrower.metadata.defaultJarName");

        final Predicate<String> filter = name -> userFiler.test(name) &&
                !name.startsWith("plexus-") &&
                !name.startsWith("animal-sniffer") &&
                !name.startsWith("winegrower-build") &&
                !name.startsWith("winegrower-core") &&
                !name.startsWith("winegrower-knight") &&
                !name.startsWith("commons-") &&
                !name.startsWith("xbean-") &&
                !name.startsWith("osgi.");
        final MetadataBuilder metadata = !generateMetadata ? null : new MetadataBuilder(metadataAutoFiltering);
        try {
            final Collection<Class<?>> classes = visitClasspath(context, filter, metadata, metadataDefaultJarName);
            registerClasses(context, classes);
            if (metadata != null && !metadata.getMetadata().isEmpty()) {
                final Path workDir = Paths.get(requireNonNull(context.getProperty("workingDirectory"), "workingDirectory property"));
                if (metadata.getMetadata().containsKey("index")) {
                    context.addNativeImageOption("-H:WinegrowerIndex=" +
                            dump(workDir, "winegrower.index.properties", metadata.getMetadata().get("index")));
                }
                if (metadata.getMetadata().containsKey("manifests")) {
                    context.addNativeImageOption("-H:WinegrowerManifests=" +
                            dump(workDir, "winegrower.manifests.properties", metadata.getMetadata().get("manifests")));
                }
            } else if (generateMetadata) {
                log.info("No winegrower metadata to dump");
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String dump(final Path workDir, final String name, final Properties index) {
        if (!java.nio.file.Files.isDirectory(workDir)) {
            try {
                java.nio.file.Files.createDirectories(workDir);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        final Path out = workDir.resolve(name);
        try (final OutputStream outputStream = java.nio.file.Files.newOutputStream(out)) {
            index.store(outputStream, name);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        log.info("Created '{}'", out);
        return out.toAbsolutePath().toString();
    }

    private void registerClasses(final Context context, final Collection<Class<?>> classes) {
        final Consumer<Class<?>> logger = log.isDebugEnabled() ? c -> log.debug("Registering '{}'", c) : c -> {};
        classes.stream().peek(logger).map(it -> {
            final ClassReflectionModel model = new ClassReflectionModel();
            model.setName(it.getName());
            model.setAllPublicConstructors(true);
            model.setAllPublicMethods(true);
            model.setAllDeclaredFields(true);
            return model;
        }).forEach(context::register);
    }

    private Collection<Class<?>> visitClasspath(final Context context, final Predicate<String> filter,
                                                final MetadataBuilder metadata, final String metadataDefaultJarName) throws IOException {
        final Collection<Class<?>> classes = new ArrayList<>();
        new UrlSet(findUrls(Thread.currentThread().getContextClassLoader()))
                .excludeJvm()
                .getUrls()
                .stream()
                .map(Files::toFile)
                .filter(file -> filter.test(file.getName()))
                .map(File::toPath)
                .forEach(jarOrDirectory -> {
                    if (java.nio.file.Files.isDirectory(jarOrDirectory)) {
                        browseDirectory(context, jarOrDirectory, classes, metadata, metadataDefaultJarName);
                    } else if (jarOrDirectory.getFileName().toString().endsWith(".jar")) {
                        browseJar(context, jarOrDirectory, classes, metadata);
                    } else {
                        log.info("Ignoring '{}'", jarOrDirectory);
                        return;
                    }
                    if (metadata != null) {
                        metadata.afterJar();
                    }
                });
        return classes;
    }

    private void browseJar(final Context context, final Path jarOrDirectory,
                           final Collection<Class<?>> classes, final MetadataBuilder metadata) {
        try (final JarFile jar = new JarFile(jarOrDirectory.toFile())) {
            if (metadata == null) { // winegrower metadata
                Stream.of("index", "manifests")
                        .map(it -> "WINEGROWER-INF/" + it + ".properties")
                        .map(jar::getEntry)
                        .filter(Objects::nonNull)
                        .forEach(it -> context.register(resource(it.getName())));
            }

            // activator if needed
            final ZipEntry manifestEntry = jar.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry != null) {
                try (final InputStream inputStream = jar.getInputStream(manifestEntry)) {
                    final Manifest manifest = handleManifest(classes, inputStream, context);
                    if (metadata != null) {
                        metadata.onJar(jarOrDirectory.getFileName().toString(), manifest);
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } else if (metadata != null) {
                metadata.onJar(jarOrDirectory.getFileName().toString(), null);
            }

            list(jar.entries()).stream()
                    .peek(e -> { // register metadata
                        if (metadata != null) {
                            metadata.onFile(e.getName());
                        }
                    })
                    // SCR and friends
                    .filter(e -> e.getName().startsWith("OSGI-INF/"))
                    .filter(e -> isOSGiInfDescriptor(e.getName()))
                    .peek(e -> {
                        if (e.getName().endsWith(".xml")) {
                            try (final InputStream stream = jar.getInputStream(e)) {
                                registerScrComponentsIfNeeded(jar + "#" + e.getName(), stream, classes, context);
                            } catch (final IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        }
                    })
                    .forEach(it -> context.register(resource("OSGI-INF/" + it.getName())));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void browseDirectory(final Context context, final Path directory,
                                 final Collection<Class<?>> classes, final MetadataBuilder metadata,
                                 final String metadataDefaultJarName) {
        // winegrower metadata
        if (metadata == null) {
            final Path winegrowerInf = directory.resolve("WINEGROWER-INF");
            if (java.nio.file.Files.isDirectory(winegrowerInf)) {
                Stream.of("index", "manifests")
                        .map(it -> it + ".properties")
                        .filter(it -> java.nio.file.Files.exists(winegrowerInf.resolve(it)))
                        .forEach(it -> context.register(resource("WINEGROWER-INF/" + it)));
            }
        }

        // activator if needed
        final Path manifest = directory.resolve("META-INF/MANIFEST.MF");
        if (java.nio.file.Files.exists(manifest)) {
            try (final InputStream inputStream = java.nio.file.Files.newInputStream(manifest)) {
                handleManifest(classes, inputStream, context);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        // SCR and friends
        final Path osgiInf = directory.resolve("OSGI-INF");
        if (java.nio.file.Files.isDirectory(osgiInf)) {
            try {
                java.nio.file.Files.list(osgiInf)
                        .filter(path -> isOSGiInfDescriptor(path.getFileName().toString()))
                        .peek(it -> {
                            if (it.getFileName().toString().endsWith(".xml")) {
                                try (final InputStream stream = java.nio.file.Files.newInputStream(it)) {
                                    registerScrComponentsIfNeeded(it.toString(), stream, classes, context);
                                } catch (final IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        })
                        .forEach(it -> context.register(resource("OSGI-INF/" + it.getFileName())));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        // finally init the metadata if needed
        if (metadata != null) {
            metadata.visitFolder(ofNullable(metadataDefaultJarName).orElseGet(() -> { // bad heuristic to not get a NPE
                final AtomicReference<Path> current = new AtomicReference<>(directory);
                while (Stream.of("classes", "target").anyMatch(it -> it.equals(current.get().getFileName().toString())) &&
                        current.get().getParent() != null &&
                        (java.nio.file.Files.exists(current.get().getParent().resolve("pom.xml")) ||
                                current.get().getParent().getParent() != null &&
                                java.nio.file.Files.exists(current.get().getParent().getParent().resolve("pom.xml")))) {
                    current.set(current.get().getParent());
                }
                return current.get().getFileName().toString();
            }), directory, new SimpleFileVisitor<Path>() {});
        }
    }

    private boolean isOSGiInfDescriptor(final String filename) {
        return filename.endsWith(".xml") || filename.endsWith(".properties");
    }

    private Manifest handleManifest(final Collection<Class<?>> classes, final InputStream inputStream, final Context context) throws IOException {
        final Manifest mf = new Manifest(inputStream);
        ofNullable(mf.getMainAttributes().getValue("Bundle-Activator")).ifPresent(activator -> {
            try {
                classes.add(context.loadClass(activator));
            } catch (final IllegalStateException e) {
                log.info("Missing class: {}", activator);
            }
        });
        return mf;
    }

    private void registerScrComponentsIfNeeded(final String source, final InputStream stream, final Collection<Class<?>> classes,
                                               final Context context) {
        try {
            ensureXmlIsInitialized();

            final Document document = documentBuilderFactory.newDocumentBuilder().parse(stream);

            xpath.reset();
            final String implementation = xpath.evaluate("/*[local-name()='component']/implementation/@class", document.getDocumentElement());
            if (implementation != null && !implementation.isEmpty()) {
                context.findHierarchy(context.loadClass(implementation)).forEach(classes::add);
            }
        } catch (final XPathExpressionException | ParserConfigurationException | IOException e) {
            throw new IllegalStateException(e);
        } catch (final SAXException sax) {
            log.warn("Can't read xml {}", source);
        } catch (final IllegalStateException e) {
            log.info("Missing class: {}", e.getMessage());
        }
    }

    private void ensureXmlIsInitialized() throws ParserConfigurationException {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            xpath = XPathFactory.newInstance().newXPath();
        }
    }

    private ResourceModel resource(final String name) {
        final ResourceModel resource = new ResourceModel();
        resource.setPattern(Pattern.quote(name));
        return resource;
    }
}

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
package org.apache.geronimo.arthur.maven.mojo;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

public abstract class JibMojo extends ArthurMojo {
    /**
     * Base image to use. Scratch will ensure it starts from an empty image.
     * For a partially linked use busybox:glibc.
     */
    @Parameter(property = "arthur.from", defaultValue = "scratch")
    private String from;

    /**
     * Ports to expose.
     */
    @Parameter(property = "arthur.ports")
    private List<String> ports;

    /**
     * Other files to include in the image, note that their permissions will not be executable.
     */
    @Parameter(property = "arthur.files")
    private List<File> otherFiles;

    /**
     * Program arguments.
     */
    @Parameter(property = "arthur.programArguments")
    private List<String> programArguments;

    /**
     * Image environment.
     */
    @Parameter(property = "arthur.environment")
    private Map<String, String> environment;

    /**
     * Image labels.
     */
    @Parameter(property = "arthur.labels")
    private Map<String, String> labels;

    /**
     * Timestamp creation for the image, it is recommended to set it fixed for reproducibility.
     */
    @Parameter(property = "arthur.creationTimestamp", defaultValue = "1")
    private long creationTimestamp;

    /**
     * Entry point to use.
     */
    @Parameter(property = "arthur.entrypoint", defaultValue = "/${project.artifactId}")
    private List<String> entrypoint;

    /**
     * Where is the binary to include. It defaults on native-image output if done before in the same execution
     */
    @Parameter(property = "arthur.binarySource")
    private File binarySource;

    /**
     * Should base images be cached.
     */
    @Parameter(property = "arthur.enableCache", defaultValue = "true")
    private boolean enableCache;

    /**
     * Are insecure registries allowed.
     */
    @Parameter(property = "arthur.allowInsecureRegistries", defaultValue = "false")
    private boolean allowInsecureRegistries;

    /**
     * Where to cache application layers.
     */
    @Parameter(property = "arthur.applicationLayersCache", defaultValue = "${project.build.directory}/arthur_jib_cache/application")
    private File applicationLayersCache;

    /**
     * Where to cache base layers layers (if any).
     */
    @Parameter(property = "arthur.baseLayersCache", defaultValue = "${project.build.directory}/arthur_jib_cache/base")
    private File baseLayersCache;

    /**
     * Number of threads used to build.
     */
    @Parameter(property = "arthur.threads", defaultValue = "1")
    private int threads;

    /**
     * Build timeout in milliseconds if it is using threads > 1.
     */
    @Parameter(property = "arthur.timeout", defaultValue = "3600000")
    private long timeout;

    /**
     * Target image name.
     */
    @Parameter(property = "arthur.to", defaultValue = "${project.artifactId}:${project.version}")
    protected String to;

    /**
     * Should JVM native libraries be included, it is useful to get libraries like sunec (security).
     * Value can be `false` to disable it (empty or null works too), `true` to include them all
     * or a list of lib names like `sunec`.
     */
    @Parameter(property = "arthur.includeNatives", defaultValue = "false")
    protected List<String> includeNatives;

    /**
     * When includeNatives, the directory which will contain the natives in the image.
     */
    @Parameter(property = "arthur.nativeRootDir", defaultValue = "/native")
    protected String nativeRootDir;

    /**
     * Should cacerts be included.
     */
    @Parameter(property = "arthur.includeCacerts", defaultValue = "false")
    protected boolean includeCacerts;

    /**
     * When includeCacerts, the file which will contain the certificates in the image.
     */
    @Parameter(property = "arthur.cacertsDir", defaultValue = "/certificates/cacerts")
    protected String cacertsTarget;

    protected abstract Containerizer createContainer() throws InvalidImageReferenceException;

    @Override
    public void execute() {
        final JibContainerBuilder prepared = prepare();
        withExecutor(es -> {
            try {
                final Containerizer containerizer = createContainer();
                final JibContainer container = prepared.containerize(configure(containerizer, es));
                if (propertiesPrefix != null) {
                    project.getProperties().setProperty(propertiesPrefix + "image.imageId", container.getImageId().getHash());
                    project.getProperties().setProperty(propertiesPrefix + "image.digest", container.getDigest().getHash());
                }
                getLog().info("Built '" + to + "'");
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void withExecutor(final Consumer<ExecutorService> consumer) {
        if (threads > 1) {
            final ExecutorService executorService = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(final Runnable r) {
                    return new Thread(r, JibMojo.class.getName() + "-" + counter.incrementAndGet());
                }
            });
            try {
                consumer.accept(executorService);
            } finally {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(timeout, MILLISECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            consumer.accept(null);
        }
    }

    private Containerizer configure(final Containerizer to, final ExecutorService executorService) {
        to.setAlwaysCacheBaseImage(enableCache);
        to.setAllowInsecureRegistries(allowInsecureRegistries);
        to.setApplicationLayersCache(applicationLayersCache.toPath());
        to.setBaseImageLayersCache(baseLayersCache.toPath());
        to.setOfflineMode(offline);
        to.setToolName("Arthur " + getClass().getSimpleName().replace("Mojo", ""));
        to.setExecutorService(executorService);
        to.addEventHandler(LogEvent.class, event -> {
            switch (event.getLevel()) {
                case INFO:
                case LIFECYCLE:
                case PROGRESS:
                    getLog().info(event.getMessage());
                    break;
                case WARN:
                    getLog().warn(event.getMessage());
                    break;
                case ERROR:
                    getLog().error(event.getMessage());
                    break;
                case DEBUG:
                default:
                    getLog().debug(event.getMessage());
                    break;
            }
        });
        return to;
    }

    private JibContainerBuilder prepare() {
        try {
            final JibContainerBuilder from = Jib.from(ImageReference.parse(this.from));
            if (ports != null) {
                from.setExposedPorts(Ports.parse(ports));
            }
            if (environment != null) {
                from.setEnvironment(environment);
            }
            if (labels != null) {
                from.setLabels(labels);
            }
            if (programArguments != null) {
                from.setProgramArguments(programArguments);
            }
            from.setCreationTime(creationTimestamp < 0 ? Instant.now() : Instant.ofEpochMilli(creationTimestamp));

            final boolean hasNatives = includeNatives != null && !includeNatives.isEmpty() && !singletonList("false").equals(includeNatives);
            if (entrypoint == null || entrypoint.size() < 1) {
                throw new IllegalArgumentException("No entrypoint set");
            }
            from.setEntrypoint(Stream.concat(Stream.concat(
                    entrypoint.stream(),
                    hasNatives ? Stream.of("-Djava.library.path=" + nativeRootDir) : Stream.empty()),
                    includeCacerts ? Stream.of("-Djavax.net.ssl.trustStore=" + cacertsTarget) : Stream.empty())
                    .collect(toList()));

            final Path source = ofNullable(binarySource)
                    .map(File::toPath)
                    .orElseGet(() -> Paths.get(requireNonNull(
                            project.getProperties().getProperty(propertiesPrefix + "binary.path"),
                            "No binary path found, ensure to run native-image before or set entrypoint")));
            from.setFileEntriesLayers(Stream.concat(Stream.concat(Stream.concat(
                    includeCacerts ?
                            Stream.of(findCertificates()) : Stream.empty(),
                    hasNatives ?
                            Stream.of(findNatives()) : Stream.empty()),
                    otherFiles != null && !otherFiles.isEmpty() ?
                            Stream.of(createOthersLayer()) :
                            Stream.empty()),
                    Stream.of(FileEntriesLayer.builder()
                            .setName("Binary")
                            .addEntry(new FileEntry(
                                    source, AbsoluteUnixPath.get(entrypoint.iterator().next()), FilePermissions.fromOctalString("755"),
                                    getTimestamp(source)))
                            .build()))
                    .collect(toList()));

            return from;
        } catch (final InvalidImageReferenceException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path findHome() {
        if (nativeImage == null) {
            return createInstaller().install();
        }
        return Paths.get(nativeImage).getParent().getParent();
    }

    private FileEntriesLayer findCertificates() {
        final Path home = findHome();
        getLog().info("Using certificates from '" + home + "'");
        final Path cacerts = home.resolve("jre/lib/security/cacerts");
        if (!Files.exists(cacerts)) {
            throw new IllegalArgumentException("Missing cacerts in '" + home + "'");
        }
        return FileEntriesLayer.builder()
                .setName("Certificates")
                .addEntry(cacerts, AbsoluteUnixPath.get(cacertsTarget))
                .build();
    }

    private FileEntriesLayer findNatives() {
        final Path home = findHome();
        getLog().info("Using natives from '" + home + "'");
        final Path jreLib = home.resolve("jre/lib");
        final boolean isWin = Files.exists(jreLib.resolve("java.lib"));
        final Path nativeFolder = isWin ?
                jreLib /* win/cygwin */ :
                jreLib.resolve(System.getProperty("os.arch", "amd64"));
        if (!Files.exists(nativeFolder)) {
            throw new IllegalArgumentException("No native folder '" + nativeFolder + "' found.");
        }
        final boolean includeAll = singletonList("true").equals(includeNatives) || singletonList("*").equals(includeNatives);
        final Predicate<Path> include = includeAll ?
                p -> true : path -> {
            final String name = path.getFileName().toString();
            return includeNatives.stream().anyMatch(n -> name.contains(isWin ? (n + ".lib") : ("lib" + n + ".so")));
        };
        final FileEntriesLayer.Builder builder = FileEntriesLayer.builder();
        final Collection<String> collected = new ArrayList<>();
        try {
            Files.walkFileTree(nativeFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (include.test(file)) {
                        collected.add(file.getFileName().toString());
                        builder.addEntry(
                                file, AbsoluteUnixPath.get(nativeRootDir).resolve(nativeFolder.relativize(file).toString()),
                                FilePermissions.DEFAULT_FILE_PERMISSIONS, getTimestamp(file));
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        if (!includeAll && collected.size() != includeNatives.size()) {
            throw new IllegalArgumentException("Found " + collected + " but was configured to extract " + includeNatives);
        }
        return builder.setName("Natives").build();
    }

    private Instant getTimestamp(final Path source) throws IOException {
        return creationTimestamp < 0 ? Files.getLastModifiedTime(source).toInstant() : Instant.ofEpochMilli(creationTimestamp);
    }

    private FileEntriesLayer createOthersLayer() {
        final FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName("Others");
        otherFiles.stream().map(File::toPath).forEach(f -> {
            final AbsoluteUnixPath containerPath = AbsoluteUnixPath.get(project.getBasedir().toPath().relativize(f).toString());
            if (containerPath.toString().contains("..")) {
                throw new IllegalArgumentException("You can only include files included in basedir");
            }
            try {
                if (Files.isDirectory(f)) {
                    builder.addEntryRecursive(
                            f, containerPath,
                            (l, c) -> FilePermissions.DEFAULT_FILE_PERMISSIONS,
                            (l, c) -> {
                                try {
                                    return getTimestamp(l);
                                } catch (final IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                } else {
                    builder.addEntry(f, containerPath, FilePermissions.DEFAULT_FILE_PERMISSIONS, getTimestamp(f));
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
        return builder.build();
    }
}

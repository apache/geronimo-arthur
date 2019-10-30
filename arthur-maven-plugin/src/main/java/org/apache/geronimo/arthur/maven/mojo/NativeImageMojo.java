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

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.util.Collections.emptyMap;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;
import static org.apache.xbean.finder.archive.ClasspathArchive.archive;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.PropertyOrderStrategy;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageExecutor;
import org.apache.geronimo.arthur.impl.nativeimage.archive.Extractor;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstaller;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstallerConfiguration;
import org.apache.geronimo.arthur.maven.extension.MavenArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.CompositeArchive;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Generates a native binary from current project.
 */
@Mojo(name = "native-image", defaultPhase = PACKAGE, requiresDependencyResolution = TEST, threadSafe = true)
public class NativeImageMojo extends ArthurMojo {
    //
    // ArthurNativeImageConfiguration
    //

    /**
     * native-image binary to use, if not set it will install graal in the local repository.
     */
    @Parameter(property = "arthur.nativeImage")
    private String nativeImage;

    /**
     * custom native-image arguments.
     */
    @Parameter(property = "arthur.customOptions")
    private List<String> customOptions;

    /**
     * custom pre-built classpath, if not set it defaults on the project dependencies.
     */
    @Parameter(property = "arthur.classpath")
    private List<String> classpath;

    /**
     * JSON java.lang.reflect.Proxy configuration.
     */
    @Parameter(property = "arthur.dynamicProxyConfigurationFiles")
    private List<String> dynamicProxyConfigurationFiles;

    /**
     * JSON reflection configuration.
     */
    @Parameter(property = "arthur.reflectionConfigurationFiles")
    private List<String> reflectionConfigurationFiles;

    /**
     * JSON resources configuration.
     */
    @Parameter(property = "arthur.resourcesConfigurationFiles")
    private List<String> resourcesConfigurationFiles;

    /**
     * resource bundle qualified names to include.
     */
    @Parameter(property = "arthur.includeResourceBundles")
    private List<String> includeResourceBundles;

    /**
     * Classes to intiialize at run time.
     */
    @Parameter(property = "arthur.initializeAtRunTime")
    private List<String> initializeAtRunTime;

    /**
     * Classes to initialize at build time.
     */
    @Parameter(property = "arthur.initializeAtBuildTime")
    private List<String> initializeAtBuildTime;

    /**
     * Limit the number of compilable methods.
     */
    @Parameter(property = "arthur.maxRuntimeCompileMethods", defaultValue = "1000")
    private int maxRuntimeCompileMethods;

    /**
     * Enforce {@link #maxRuntimeCompileMethods}.
     */
    @Parameter(property = "arthur.enforceMaxRuntimeCompileMethods", defaultValue = "true")
    private boolean enforceMaxRuntimeCompileMethods;

    /**
     * Should all charsets be added.
     */
    @Parameter(property = "arthur.addAllCharsets", defaultValue = "true")
    private boolean addAllCharsets;

    /**
     * Should exception stacks be reported.
     */
    @Parameter(property = "arthur.reportExceptionStackTraces", defaultValue = "true")
    private boolean reportExceptionStackTraces;

    /**
     * Should class initialition be tracked.
     */
    @Parameter(property = "arthur.traceClassInitialization", defaultValue = "true")
    private boolean traceClassInitialization;

    /**
     * Should initialiation of classes be printed - mainly for debug purposes.
     */
    @Parameter(property = "arthur.printClassInitialization", defaultValue = "false")
    private boolean printClassInitialization;

    /**
     * Behavior when native compilation fails, it is recommended to keep it to "no".
     */
    @Parameter(property = "arthur.fallbackMode", defaultValue = "no")
    private ArthurNativeImageConfiguration.FallbackMode fallbackMode;

    /**
     * Should the image be static or dynamic (jvm part).
     */
    @Parameter(property = "arthur.buildStaticImage", defaultValue = "true")
    private boolean buildStaticImage;

    /**
     * Should incomplete classpath be tolerated.
     */
    @Parameter(property = "arthur.allowIncompleteClasspath", defaultValue = "true")
    private boolean allowIncompleteClasspath;

    /**
     * Should unsupported element be reported at runtime or not. It is not a recommended option but it is often needed.
     */
    @Parameter(property = "arthur.reportUnsupportedElementsAtRuntime", defaultValue = "true")
    private boolean reportUnsupportedElementsAtRuntime;

    /**
     * Should security services be included.
     */
    @Parameter(property = "arthur.enableAllSecurityServices", defaultValue = "true")
    private boolean enableAllSecurityServices;

    /**
     * Which main to compile.
     */
    @Parameter(property = "arthur.main", required = true)
    private String main;

    /**
     * Where to put the output binary.
     */
    @Parameter(property = "arthur.output", defaultValue = "${project.build.directory}/${project.artifactId}.graal.bin")
    private String output;

    /**
     * The execution will fork native-image process, should IO be inherited from maven process (recommended).
     */
    @Parameter(property = "arthur.inheritIO", defaultValue = "true")
    private boolean inheritIO;

    /**
     * Should graal build server be used (a bit like gradle daemon), it is very discouraged to be used cause invalidation is not yet well handled.
     */
    @Parameter(property = "arthur.noServer", defaultValue = "true")
    private boolean noServer;

    //
    // Installer parameters
    //

    /**
     * In case Graal must be downloaded to get native-image, where to take it from.
     */
    @Parameter(property = "arthur.graalDownloadUrl",
            defaultValue = "https://api.sdkman.io/2/broker/download/java/${graalVersion}-grl/${platform}")
    private String graalDownloadUrl;

    /**
     * In case Graal must be downloaded to get native-image, which version to download.
     */
    @Parameter(property = "arthur.graalVersion", defaultValue = "19.2.1")
    private String graalVersion;

    /**
     * In case Graal must be downloaded to get native-image, which platform to download, auto will handle it for you.
     */
    @Parameter(property = "arthur.graalPlatform", defaultValue = "auto")
    private String graalPlatform;

    /**
     * In case Graal must be downloaded to get native-image, it will be cached in the local repository with this gav.
     */
    @Parameter(property = "arthur.graalCacheGav", defaultValue = "org.apache.geronimo.arthur.cache:graal")
    private String graalCacheGav; // groupId:artifactId

    //
    // Other maven injections
    //

    /**
     * Inline configuration model (appended to {@link #reflectionConfigurationFiles}.
     */
    @Parameter
    private List<ClassReflectionModel> reflections;

    /**
     * Inline resource bundle model (appended to {@link #reflectionConfigurationFiles}.
     */
    @Parameter
    private List<ResourceBundleModel> bundles;

    /**
     * Inline resources model (appended to {@link #resourcesConfigurationFiles}.
     */
    @Parameter
    private List<ResourceModel> resources;

    /**
     * Inline dynamic proxy configuration (appended to {@link #dynamicProxyConfigurationFiles}).
     */
    @Parameter
    private List<DynamicProxyModel> dynamicProxies;

    /**
     * Should this mojo be skipped.
     */
    @Parameter(property = "arthur.skip")
    private boolean skip;

    /**
     * Should the build be done with test dependencies (and binaries).
     */
    @Parameter(property = "arthur.supportTestArtifacts", defaultValue = "false")
    private boolean supportTestArtifacts;

    /**
     * By default arthur runs the extension with a dedicated classloader built from the project having as parent the JVM,
     * this enables to use the mojo as parent instead).
     */
    @Parameter(property = "arthur.useTcclAsScanningParentClassLoader", defaultValue = "false")
    private boolean useTcclAsScanningParentClassLoader;

    /**
     * Where the temporary files are created.
     */
    @Parameter(defaultValue = "${project.build.directory}/arthur_workdir")
    private File workdir;

    /**
     * groupId:artifactId list of ignored artifact during the pre-build phase.
     */
    @Parameter(property = "arthur.excludedArtifacts")
    private List<String> excludedArtifacts;

    /**
     * {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>} list of artifacts appended to graal build.
     */
    @Parameter(property = "arthur.graalExtensions")
    private List<String> graalExtensions;

    /**
     * List of types used in the build classpath, typically enables to ignore tar.gz/natives for example.
     */
    @Parameter(property = "arthur.supportedTypes", defaultValue = "jar,zip")
    private List<String> supportedTypes;

    /**
     * Should jar be used instead of exploded folder (target/classes).
     * Note this option disable the support of module test classes.
     */
    @Parameter(property = "project.usePackagedArtifact", defaultValue = "false")
    private boolean usePackagedArtifact;

    @Parameter(defaultValue = "${project.packaging}", readonly = true)
    private String packaging;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File jar;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classes;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}")
    private File testClasses;

    @Parameter(defaultValue = "${repositorySystemSession}")
    protected RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepositories;

    @Component
    private RepositorySystem repositorySystem;

    @Override
    public void execute() {
        if (skip) {
            getLog().info("Skipping execution as requested");
            return;
        }
        if ("pom".equals(packaging)) {
            getLog().info("Skipping packaging pom");
            return;
        }

        final List<File> classpathFiles = findClasspathFiles().collect(toList());

        final ArthurNativeImageConfiguration configuration = getConfiguration(classpathFiles);
        if (nativeImage == null) {
            final String graalPlatform = buildPlatform();
            final Extractor extractor = new Extractor();
            final SdkmanGraalVMInstaller graalInstaller = new SdkmanGraalVMInstaller(SdkmanGraalVMInstallerConfiguration.builder()
                    .offline(offline)
                    .inheritIO(inheritIO)
                    .url(buildDownloadUrl(graalPlatform))
                    .version(graalVersion)
                    .platform(graalPlatform)
                    .gav(buildCacheGav(graalPlatform))
                    .workdir(workdir.toPath())
                    .resolver(gav -> resolve(toArtifact(gav)).getFile().toPath())
                    .installer((gav, file) -> install(file.toFile(), toArtifact(gav)))
                    .extractor(extractor::unpack)
                    .build());
            final Path graalHome = graalInstaller.install();
            getLog().info("Using GRAAL: " + graalHome);
            configuration.setNativeImage(graalInstaller.installNativeImage().toAbsolutePath().toString());
        }

        final URL[] urls = classpathFiles.stream().map(it -> {
            try {
                return it.toURI().toURL();
            } catch (final MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }).toArray(URL[]::new);
        final Thread thread = Thread.currentThread();
        final ClassLoader parentLoader = useTcclAsScanningParentClassLoader ?
                thread.getContextClassLoader() : getSystemClassLoader();
        final ClassLoader oldLoader = thread.getContextClassLoader();
        try (final URLClassLoader loader = new URLClassLoader(urls, parentLoader);
             final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
                     .setProperty("johnzon.cdi.activated", false)
                     .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL))) {
            thread.setContextClassLoader(loader);
            final AnnotationFinder finder = new AnnotationFinder(new CompositeArchive(Stream.of(urls)
                    .map(it -> archive(loader, it))
                    .collect(toList())));
            MavenArthurExtension.with(
                    reflections, resources, bundles, dynamicProxies,
                    () -> new ArthurNativeImageExecutor(
                            ArthurNativeImageExecutor.ExecutorConfiguration.builder()
                                    .jsonSerializer(jsonb::toJson)
                                    .finder(finder::findAnnotatedClasses)
                                    .configuration(configuration)
                                    .workingDirectory(workdir.toPath().resolve("generated_configuration"))
                                    .build())
                            .run());
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        } finally {
            thread.setContextClassLoader(oldLoader);
        }

        if (propertiesPrefix != null) {
            project.getProperties().setProperty(propertiesPrefix + "binary.path", output);
        }
    }

    private String buildCacheGav(final String graalPlatform) {
        if (!graalPlatform.toLowerCase(ROOT).startsWith("linux")) { // cygwin
            return graalCacheGav + ":zip:" + graalPlatform + ':' + graalVersion;
        }
        return graalCacheGav + ":tar.gz:" + graalPlatform + ':' + graalVersion;
    }

    private String buildDownloadUrl(final String graalPlatform) {
        return graalDownloadUrl
                .replace("${graalVersion}", graalVersion)
                .replace("${platform}", graalPlatform);
    }

    private String buildPlatform() {
        if (!"auto".equals(graalPlatform)) {
            return graalPlatform;
        }
        return (System.getProperty("os.name", "linux") +
                ofNullable(System.getProperty("sun.arch.data.model"))
                        .orElseGet(() -> System.getProperty("os.arch", "64").replace("amd", "")))
                .toLowerCase(ROOT);
    }

    private Stream<File> findClasspathFiles() {
        return Stream.concat(Stream.concat(
                usePackagedArtifact ?
                        Stream.of(jar) :
                        Stream.concat(
                                Stream.of(classes),
                                supportTestArtifacts ? Stream.of(testClasses) : Stream.empty()),
                project.getArtifacts().stream()
                        .filter(a -> !excludedArtifacts.contains(a.getGroupId() + ':' + a.getArtifactId()))
                        .filter(this::handleTestInclusion)
                        .filter(this::isNotSvm)
                        .filter(a -> supportedTypes.contains(a.getType()))
                        .map(Artifact::getFile)),
                resolveExtension())
                .filter(File::exists);
    }

    private boolean handleTestInclusion(final Artifact a) {
        return !"test".equals(a.getScope()) || supportTestArtifacts;
    }

    private boolean isNotSvm(final Artifact artifact) {
        return !"com.oracle.substratevm".equals(artifact.getGroupId());
    }

    private Stream<File> resolveExtension() {
        return ofNullable(graalExtensions)
                .map(e -> e.stream()
                        .map(this::toArtifact)
                        .map(this::resolve)
                        .map(org.eclipse.aether.artifact.Artifact::getFile))
                .orElseGet(Stream::empty);
    }

    private org.eclipse.aether.artifact.Artifact toArtifact(final String s) {
        return new DefaultArtifact(s);
    }

    private Path install(final File file, final org.eclipse.aether.artifact.Artifact art) {
        final org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(
                art.getGroupId(),
                art.getArtifactId(),
                art.getClassifier(),
                art.getExtension(),
                art.getVersion(),
                emptyMap(),
                file);
        try {
            final InstallResult result = repositorySystem.install(
                    repositorySystemSession,
                    new InstallRequest().addArtifact(artifact));
            if (result.getArtifacts().isEmpty()) {
                throw new IllegalStateException("Can't install " + art);
            }
            return resolve(art).getFile().toPath();
        } catch (final InstallationException e) {
            throw new IllegalStateException(e);
        }
    }

    private org.eclipse.aether.artifact.Artifact resolve(final org.eclipse.aether.artifact.Artifact art) {
        final ArtifactRequest artifactRequest =
                new ArtifactRequest().setArtifact(art).setRepositories(remoteRepositories);
        try {
            final ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
            if (result.isMissing()) {
                throw new IllegalStateException("Can't find " + art);
            }
            return result.getArtifact();
        } catch (final ArtifactResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private ArthurNativeImageConfiguration getConfiguration(final Collection<File> classpathFiles) {
        final ArthurNativeImageConfiguration configuration = new ArthurNativeImageConfiguration();
        Stream.of(ArthurNativeImageConfiguration.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ArthurNativeImageConfiguration.GraalCommandPart.class))
                .map(this::asAccessible)
                .forEach(field -> {
                    try {
                        final Field mojoField = asAccessible(NativeImageMojo.class.getDeclaredField(field.getName()));
                        field.set(configuration, mojoField.get(NativeImageMojo.this));
                    } catch (final NoSuchFieldException | IllegalAccessException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
        if (configuration.getClasspath() == null || configuration.getClasspath().isEmpty()) {
            configuration.setClasspath(classpathFiles.stream().map(File::getAbsolutePath).collect(toList()));
        }
        configuration.setInheritIO(inheritIO);
        return configuration;
    }

    private Field asAccessible(final Field field) {
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        return field;
    }
}

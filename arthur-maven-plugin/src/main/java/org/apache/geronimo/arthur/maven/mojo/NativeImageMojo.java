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

import lombok.Getter;
import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageExecutor;
import org.apache.geronimo.arthur.impl.nativeimage.generator.extension.AnnotationExtension;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstaller;
import org.apache.geronimo.arthur.maven.extension.MavenArthurExtension;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.CompositeArchive;
import org.apache.xbean.finder.archive.FilteredArchive;
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.Filters;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.PropertyOrderStrategy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;
import static org.apache.xbean.finder.archive.ClasspathArchive.archive;

/**
 * Generates a native binary from current project.
 */
@Mojo(name = "native-image", defaultPhase = PACKAGE, requiresDependencyResolution = TEST, threadSafe = true)
public class NativeImageMojo extends ArthurMojo {
    //
    // ArthurNativeImageConfiguration
    //

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
     * Enforce `maxRuntimeCompileMethods`.
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
     * Should initialiation of classes be printed - mainly for debug purposes.
     */
    @Parameter(property = "arthur.printClassInitialization", defaultValue = "false")
    private boolean printClassInitialization;

    /**
     * Behavior when native compilation fails, it is recommended to keep it to "no".
     * Supported values are `no`, `force` and `auto`.
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
    @Getter(PROTECTED)
    @Parameter(property = "arthur.inheritIO", defaultValue = "true")
    private boolean inheritIO;

    /**
     * Should graal build server be used (a bit like gradle daemon), it is very discouraged to be used cause invalidation is not yet well handled.
     */
    @Parameter(property = "arthur.noServer", defaultValue = "true")
    private boolean noServer;

    //
    // Other maven injections
    //

    /**
     * Inline configuration model (appended to `reflectionConfigurationFiles`).
     */
    @Parameter
    private List<ClassReflectionModel> reflections;

    /**
     * Inline resource bundle model (appended to `reflectionConfigurationFiles`).
     */
    @Parameter
    private List<ResourceBundleModel> bundles;

    /**
     * Inline resources model (appended to `resourcesConfigurationFiles`).
     */
    @Parameter
    private List<ResourceModel> resources;

    /**
     * Inline dynamic proxy configuration (appended to `dynamicProxyConfigurationFiles`).
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
     * groupId:artifactId list of ignored artifact during the pre-build phase.
     */
    @Parameter(property = "arthur.excludedArtifacts")
    private List<String> excludedArtifacts;

    /**
     * Classes or packages (startsWith is used to test entries).
     */
    @Parameter(property = "arthur.scanningClassesOrPackagesExcludes")
    private List<String> scanningClassesOrPackagesExcludes;

    /**
     * groupId:artifactId list of ignored artifact during the scanning phase.
     * Compared to `excludedArtifacts`, it keeps the jar in the scanning/extension classloader
     * but it does not enable to find any annotation in it.
     * Note that putting `*` will disable the scanning completely.
     */
    @Parameter(property = "arthur.scanningExcludedArtifacts")
    private List<String> scanningExcludedArtifacts;

    /**
     * `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>` list of artifacts appended to graal build.
     * If you don't want transitive dependencies to be included, you can append to the coordinates `?transitive=false`.
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
    @Parameter(property = "arthur.usePackagedArtifact", defaultValue = "false")
    private boolean usePackagedArtifact;

    /**
     * Should binary artifact be attached.
     */
    @Parameter(property = "arthur.attach", defaultValue = "true")
    private boolean attach;

    /**
     * If `attach` is true, the classifier to use the binary file, `none` will skip the classifier.
     */
    @Parameter(property = "arthur.attachClassifier", defaultValue = "arthur")
    private String attachClassifier;

    /**
     * If `attach` is true, the type to use to attach the binary file.
     */
    @Parameter(property = "arthur.attachType", defaultValue = "bin")
    private String attachType;

    /**
     * Properties passed to the extensions if needed.
     */
    @Parameter
    private Map<String, String> extensionProperties;

    @Parameter(defaultValue = "${project.packaging}", readonly = true)
    private String packaging;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.groupId}", readonly = true)
    private String groupId;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File jar;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classes;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}")
    private File testClasses;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Component
    private DependencyGraphBuilder graphBuilder;

    @Component
    private MavenProjectHelper helper;

    private String cachedVersion;

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

        final Map<Artifact, Path> classpathEntries = findClasspathFiles().collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        final ArthurNativeImageConfiguration configuration = getConfiguration(classpathEntries.values());
        if (nativeImage == null) {
            final SdkmanGraalVMInstaller graalInstaller = createInstaller();
            final Path graalHome = graalInstaller.install();
            getLog().info("Using GRAAL: " + graalHome);
            configuration.setNativeImage(graalInstaller.installNativeImage().toAbsolutePath().toString());
        }

        final URL[] urls = classpathEntries.values().stream()
                .map(it -> {
                    try {
                        return it.toUri().toURL();
                    } catch (final MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                }).toArray(URL[]::new);
        final Thread thread = Thread.currentThread();
        final ClassLoader parentLoader = useTcclAsScanningParentClassLoader ?
                thread.getContextClassLoader() : getSystemClassLoader();
        final ClassLoader oldLoader = thread.getContextClassLoader();
        try (final URLClassLoader loader = new URLClassLoader(urls, parentLoader) {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                if (name != null) {
                    if (name.startsWith("org.")) {
                        final String org = name.substring("org.".length());
                        if (org.startsWith("slf4j.")) {
                            return oldLoader.loadClass(name);
                        }
                        if (org.startsWith("apache.geronimo.arthur.")) {
                            final String arthur = org.substring("apache.geronimo.arthur.".length());
                            if (arthur.startsWith("api.") || arthur.startsWith("spi") || arthur.startsWith("impl")) {
                                return oldLoader.loadClass(name);
                            }
                        }
                    }
                }
                return super.loadClass(name, resolve);
            }
        }; final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
                .setProperty("johnzon.cdi.activated", false)
                .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL))) {
            thread.setContextClassLoader(loader);
            final Predicate<Artifact> scanningFilter = createScanningFilter();
            final Function<Archive, Archive> archiveProcessor = createArchiveFilter();
            final AnnotationFinder finder = new AnnotationFinder(archiveProcessor.apply(new CompositeArchive(classpathEntries.entrySet().stream()
                    .filter(e -> scanningFilter.test(e.getKey()))
                    .map(Map.Entry::getValue)
                    .map(path -> {
                        try {
                            return archive(loader, path.toUri().toURL());
                        } catch (final MalformedURLException e) { // unlikely
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(toList()))));
            final AtomicBoolean finderLinked = new AtomicBoolean();
            MavenArthurExtension.with(
                    reflections, resources, bundles, dynamicProxies,
                    () -> new ArthurNativeImageExecutor(
                            ArthurNativeImageExecutor.ExecutorConfiguration.builder()
                                    .jsonSerializer(jsonb::toJson)
                                    .annotatedClassFinder(finder::findAnnotatedClasses)
                                    .annotatedFieldFinder(finder::findAnnotatedFields)
                                    .annotatedMethodFinder(finder::findAnnotatedMethods)
                                    .extensionProperties(getExtensionProperties())
                                    .implementationFinder(p -> {
                                        if (finderLinked.compareAndSet(false, true)) {
                                            finder.enableFindImplementations().enableFindSubclasses();
                                        }
                                        final Class parent = Class.class.cast(p);
                                        final List<Class<?>> implementations = finder.findImplementations(parent);
                                        final List<Class<?>> subclasses = finder.findSubclasses(parent);
                                        if (implementations.size() + subclasses.size() == 0) {
                                            return implementations; // empty
                                        }
                                        final List<Class<?>> output = new ArrayList<>(implementations.size() + subclasses.size());
                                        output.addAll(implementations);
                                        output.addAll(subclasses);
                                        return output;
                                    })
                                    .configuration(configuration)
                                    .workingDirectory(workdir.toPath().resolve("generated_configuration"))
                                    .build()) {
                        @Override
                        protected Iterable<ArthurExtension> loadExtensions() {
                            return Stream.concat(
                                    // classloading bypasses them since TCCL is a fake loader with the JVM as parent
                                    Stream.of(new AnnotationExtension(), new MavenArthurExtension()),
                                    // graalextensions
                                    StreamSupport.stream(super.loadExtensions().spliterator(), false))
                                    // ensure we dont duplicate any extension
                                    .distinct()
                                    .sorted(comparing(ArthurExtension::order))
                                    .collect(toList());
                        }
                    }
                            .run());
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        } finally {
            thread.setContextClassLoader(oldLoader);
        }

        if (propertiesPrefix != null) {
            project.getProperties().setProperty(propertiesPrefix + "binary.path", output);
        }

        if (attach) {
            if (!"none".equals(attachClassifier) && attachClassifier != null && !attachClassifier.isEmpty()) {
                helper.attachArtifact(project, attachType, attachClassifier, new File(output));
            } else {
                helper.attachArtifact(project, attachType, new File(output));
            }
        }
    }

    private Function<Archive, Archive> createArchiveFilter() {
        if (scanningClassesOrPackagesExcludes == null || scanningClassesOrPackagesExcludes.isEmpty()) {
            return Function.identity();
        }
        final Filter filter = Filters.invert(Filters.prefixes(
                scanningClassesOrPackagesExcludes.stream()
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .distinct()
                        .toArray(String[]::new)));
        return a -> new FilteredArchive(a, filter);
    }

    private Map<String, String> getExtensionProperties() {
        final Map<String, String> props = extensionProperties == null ? new HashMap<>() : new HashMap<>(extensionProperties);
        props.putIfAbsent("classes", project.getBuild().getOutputDirectory());
        return props;
    }

    private Predicate<Artifact> createScanningFilter() {
        if (scanningExcludedArtifacts != null && scanningExcludedArtifacts.contains("*")) {
            return a -> false;
        }
        if (scanningExcludedArtifacts == null || scanningExcludedArtifacts.isEmpty()) {
            return a -> true;
        }
        return a -> {
            final String coord = a.getGroupId() + ':' + a.getArtifactId();
            return scanningExcludedArtifacts.stream().noneMatch(it -> it.equals(coord));
        };
    }

    private Stream<? extends Map.Entry<? extends Artifact, Path>> findClasspathFiles() {
        final Artifact artifactGav = new org.apache.maven.artifact.DefaultArtifact(
                groupId, artifactId, version, "compile", packaging, null, new DefaultArtifactHandler());
        return Stream.concat(Stream.concat(
                usePackagedArtifact ?
                        Stream.of(jar).map(j -> new AbstractMap.SimpleImmutableEntry<>(artifactGav, j.toPath())) :
                        Stream.concat(
                                Stream.of(classes).map(j -> new AbstractMap.SimpleImmutableEntry<>(artifactGav, j.toPath())),
                                supportTestArtifacts ? Stream.of(testClasses).<Map.Entry<Artifact, Path>>map(j ->
                                        new AbstractMap.SimpleImmutableEntry<>(new org.apache.maven.artifact.DefaultArtifact(
                                                groupId, artifactId, version, "compile", packaging, "test", new DefaultArtifactHandler()),
                                                j.toPath())) :
                                        Stream.empty()),
                project.getArtifacts().stream()
                        .filter(a -> !excludedArtifacts.contains(a.getGroupId() + ':' + a.getArtifactId()))
                        .filter(this::handleTestInclusion)
                        .filter(this::isNotSvm)
                        .filter(a -> supportedTypes.contains(a.getType()))
                        .map(a -> new AbstractMap.SimpleImmutableEntry<>(a, a.getFile().toPath()))),
                resolveExtension())
                .filter(e -> Files.exists(e.getValue()));
    }

    private boolean handleTestInclusion(final Artifact a) {
        return !"test".equals(a.getScope()) || supportTestArtifacts;
    }

    private boolean isNotSvm(final Artifact artifact) {
        return !"com.oracle.substratevm".equals(artifact.getGroupId()) &&
                !"org.graalvm.nativeimage".equals(artifact.getGroupId());
    }

    private Stream<? extends Map.Entry<? extends Artifact, Path>> resolveExtension() {
        return ofNullable(graalExtensions)
                .map(e -> e.stream()
                        .map(this::prepareExtension)
                        .flatMap(art -> art.endsWith("?transitive=false") ?
                                Stream.of(toArtifact(art)) :
                                resolveTransitiveDependencies(toArtifact(art)))
                        .map(it -> new AbstractMap.SimpleImmutableEntry<>(
                                toMavenArtifact(it),
                                resolve(it).getFile().toPath())))
                .orElseGet(Stream::empty);
    }

    private Stream<org.eclipse.aether.artifact.Artifact> resolveTransitiveDependencies(final org.eclipse.aether.artifact.Artifact artifact) {
        final Dependency rootDependency = new Dependency();
        rootDependency.setGroupId(artifact.getGroupId());
        rootDependency.setArtifactId(artifact.getArtifactId());
        rootDependency.setVersion(artifact.getVersion());
        rootDependency.setClassifier(artifact.getClassifier());
        rootDependency.setType(artifact.getExtension());

        final MavenProject fakeProject = new MavenProject();
        fakeProject.setRemoteArtifactRepositories(project.getRemoteArtifactRepositories());
        fakeProject.setSnapshotArtifactRepository(project.getDistributionManagementArtifactRepository());
        fakeProject.setPluginArtifactRepositories(project.getPluginArtifactRepositories());
        fakeProject.getDependencies().add(rootDependency);

        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(fakeProject);
        request.setRepositorySession(repositorySystemSession);

        final Collection<org.eclipse.aether.artifact.Artifact> artifacts = new ArrayList<>();
        try {
            dependenciesResolver.resolve(request).getDependencyGraph().accept(new DependencyVisitor() {
                @Override
                public boolean visitEnter(org.eclipse.aether.graph.DependencyNode node) {
                    return true;
                }

                @Override
                public boolean visitLeave(org.eclipse.aether.graph.DependencyNode node) {
                    final org.eclipse.aether.artifact.Artifact artifact = node.getArtifact();
                    if (artifact == null) {
                        if (node.getChildren() != null) {
                            node.getChildren().stream()
                                    .map(DependencyNode::getArtifact)
                                    .filter(Objects::nonNull)
                                    .forEach(artifacts::add);
                        } else {
                            getLog().warn(node + " has no artifact");
                        }
                    } else {
                        artifacts.add(artifact);
                    }
                    return true;
                }
            });
            return artifacts.stream().map(NativeImageMojo.this::resolve);
        } catch (final DependencyResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // support short name for our knights
    private String prepareExtension(final String ext) {
        if (ext.contains(":")) {
            return ext;
        }
        return "org.apache.geronimo.arthur.knights:" + ext + (ext.endsWith("-knight") ? "" : "-knight") + ':' + lookupVersion();
    }

    private String lookupVersion() {
        if (cachedVersion == null) {
            final Properties properties = new Properties();
            try (final InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("META-INF/maven/org.apache.geronimo.arthur/arthur-maven-plugin/pom.properties")) {
                properties.load(stream);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            cachedVersion = properties.getProperty("version");
        }
        return cachedVersion;
    }

    private org.apache.maven.artifact.DefaultArtifact toMavenArtifact(final org.eclipse.aether.artifact.Artifact it) {
        return new org.apache.maven.artifact.DefaultArtifact(
                it.getGroupId(), it.getArtifactId(), it.getVersion(), "compile",
                it.getExtension(), it.getClassifier(), new DefaultArtifactHandler());
    }

    private ArthurNativeImageConfiguration getConfiguration(final Collection<Path> classpathFiles) {
        final ArthurNativeImageConfiguration configuration = new ArthurNativeImageConfiguration();
        Stream.of(ArthurNativeImageConfiguration.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ArthurNativeImageConfiguration.GraalCommandPart.class))
                .map(this::asAccessible)
                .forEach(field -> {
                    final Class<?> fieldHolder;
                    if ("nativeImage".equals(field.getName())) {
                        fieldHolder = ArthurMojo.class;
                    } else {
                        fieldHolder = NativeImageMojo.class;
                    }
                    try {
                        final Field mojoField = asAccessible(fieldHolder.getDeclaredField(field.getName()));
                        field.set(configuration, mojoField.get(NativeImageMojo.this));
                    } catch (final NoSuchFieldException | IllegalAccessException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
        if (configuration.getClasspath() == null || configuration.getClasspath().isEmpty()) {
            configuration.setClasspath(classpathFiles.stream().map(Path::toAbsolutePath).map(Object::toString).collect(toList()));
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

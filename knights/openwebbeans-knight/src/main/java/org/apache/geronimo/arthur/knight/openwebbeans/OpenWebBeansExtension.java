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
package org.apache.geronimo.arthur.knight.openwebbeans;

import lombok.extern.slf4j.Slf4j;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.openwebbeans.se.CDISeScannerService;
import org.apache.openwebbeans.se.PreScannedCDISeScannerService;
import org.apache.webbeans.component.AbstractProducerBean;
import org.apache.webbeans.component.CdiInterceptorBean;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.config.OpenWebBeansConfiguration;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.conversation.ConversationImpl;
import org.apache.webbeans.corespi.DefaultSingletonService;
import org.apache.webbeans.corespi.se.DefaultScannerService;
import org.apache.webbeans.corespi.se.SimpleApplicationBoundaryService;
import org.apache.webbeans.corespi.se.StandaloneContextsService;
import org.apache.webbeans.inject.impl.InjectionPointImpl;
import org.apache.webbeans.intercept.ApplicationScopedBeanInterceptorHandler;
import org.apache.webbeans.intercept.NormalScopedBeanInterceptorHandler;
import org.apache.webbeans.intercept.RequestScopedBeanInterceptorHandler;
import org.apache.webbeans.intercept.SessionScopedBeanInterceptorHandler;
import org.apache.webbeans.lifecycle.StandaloneLifeCycle;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.portable.BaseProducerProducer;
import org.apache.webbeans.portable.ProducerFieldProducer;
import org.apache.webbeans.portable.ProducerMethodProducer;
import org.apache.webbeans.portable.events.ExtensionLoader;
import org.apache.webbeans.service.ClassLoaderProxyService;
import org.apache.webbeans.service.DefaultLoaderService;
import org.apache.webbeans.spi.ApplicationBoundaryService;
import org.apache.webbeans.spi.BeanArchiveService;
import org.apache.webbeans.spi.ContainerLifecycle;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.spi.DefiningClassService;
import org.apache.webbeans.spi.InjectionPointService;
import org.apache.webbeans.spi.JNDIService;
import org.apache.webbeans.spi.LoaderService;
import org.apache.webbeans.spi.ResourceInjectionService;
import org.apache.webbeans.spi.ScannerService;
import org.apache.webbeans.spi.SecurityService;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.filter.Filter;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.NormalScope;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.event.Reception;
import javax.enterprise.event.TransactionPhase;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.Bean;
import javax.inject.Qualifier;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.geronimo.arthur.spi.ArthurExtension.PredicateType.EQUALS;

@Slf4j
public class OpenWebBeansExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        final Properties original = new Properties();
        original.putAll(System.getProperties());
        try (final SeContainer container = configureInitializer(context, SeContainerInitializer.newInstance()).initialize()) {
            final WebBeansContext webBeansContext = WebBeansContext.currentInstance();
            final BeanManagerImpl beanManager = webBeansContext.getBeanManagerImpl();
            final Set<Bean<?>> beans = beanManager.getBeans();
            final Predicate<String> classFilter = context.createIncludesExcludes(
                    "extension.openwebbeans.classes.filter.", PredicateType.STARTS_WITH);

            // 1. capture all proxies
            dumpProxies(context, webBeansContext, beans, classFilter);

            // 2. register all classes which will require reflection + proxies
            final String beanClassesList = registerBeansForReflection(context, beans, classFilter);
            getProxies(webBeansContext).keySet().stream().filter(classFilter).sorted().forEach(name -> {
                final ClassReflectionModel model = new ClassReflectionModel();
                model.setName(name);
                model.setAllDeclaredConstructors(true);
                model.setAllDeclaredFields(true);
                model.setAllDeclaredMethods(true);
                context.register(model);
            });

            // 3. dump owb properties for runtime
            final Properties properties = initProperties(context, webBeansContext.getOpenWebBeansConfiguration(), beanClassesList);

            // 4. register CDI/OWB API which require some reflection
            // 4.1 SPI (interface)
            Stream.of(
                            ScannerService.class, LoaderService.class, BeanArchiveService.class, SecurityService.class,
                            ContainerLifecycle.class, JNDIService.class, ApplicationBoundaryService.class, ContextsService.class,
                            InjectionPointService.class, ResourceInjectionService.class, DefiningClassService.class,
                            Filter.class)
                    .forEach(clazz -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(clazz.getName());
                        model.setAllPublicMethods(true);
                        context.register(model);
                    });
            // 4.2 classes which must be instantiable
            Stream.concat(Stream.of(
                                    ClassLoaderProxyService.LoadOnly.class, StandaloneLifeCycle.class, StandaloneContextsService.class,
                                    DefaultLoaderService.class, InjectionPointImpl.class, ConversationImpl.class, SimpleApplicationBoundaryService.class,
                                    ApplicationScopedBeanInterceptorHandler.class, RequestScopedBeanInterceptorHandler.class,
                                    SessionScopedBeanInterceptorHandler.class, NormalScopedBeanInterceptorHandler.class,
                                    CDISeScannerService.class, PreScannedCDISeScannerService.class, DefaultScannerService.class),
                            findServices(properties))
                    .distinct()
                    .forEach(clazz -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(clazz.getName());
                        model.setAllDeclaredConstructors(true);
                        context.register(model);
                    });
            // 4.3 needed by prescanned scanner
            final ClassReflectionModel owbFinder = new ClassReflectionModel();
            owbFinder.setName(AnnotationFinder.class.getName());
            final ClassReflectionModel.FieldReflectionModel owbFinderLinking = new ClassReflectionModel.FieldReflectionModel();
            owbFinderLinking.setAllowWrite(true);
            owbFinderLinking.setName("linking");
            owbFinder.setFields(singletonList(owbFinderLinking));
            context.register(owbFinder);
            // 5 annotations
            final Collection<Class<?>> customAnnotations = Stream.concat(
                            context.findAnnotatedClasses(Qualifier.class).stream(),
                            context.findAnnotatedClasses(NormalScope.class).stream())
                    .collect(toList());
            Stream.concat(Stream.concat(Stream.of(
                                            Initialized.class, Destroyed.class, NormalScope.class, ApplicationScoped.class, Default.class,
                                            Dependent.class, ConversationScoped.class, RequestScoped.class, Observes.class, ObservesAsync.class,
                                            Qualifier.class, InterceptorBinding.class, Priority.class),
                                    beanManager.getAdditionalQualifiers().stream()),
                            customAnnotations.stream())
                    .distinct()
                    .map(Class::getName)
                    .sorted()
                    .forEach(clazz -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(clazz);
                        model.setAllDeclaredMethods(true);
                        context.register(model);
                    });
            customAnnotations.stream() // DefaultAnnotation.of
                    .filter(it -> !it.getName().startsWith("javax.") && !it.getName().startsWith("jakarta."))
                    .map(Class::getName)
                    .sorted()
                    .map(it -> {
                        final DynamicProxyModel proxyModel = new DynamicProxyModel();
                        proxyModel.setClasses(singleton(it));
                        return proxyModel;
                    })
                    .forEach(context::register);

            // 6 extensions - normally taken by graalvm service loader but we need a bit more reflection
            final ExtensionLoader extensionLoader = webBeansContext.getExtensionLoader();
            try {
                final Field extensionClasses = ExtensionLoader.class.getDeclaredField("extensionClasses");
                if (!extensionClasses.isAccessible()) {
                    extensionClasses.setAccessible(true);
                }
                final Predicate<String> extensionFilter = context.createPredicate("extension.openwebbeans.extension.excludes", PredicateType.STARTS_WITH)
                        .map(Predicate::negate)
                        .orElseGet(() -> n -> true);
                final Set<Class<?>> classes = (Set<Class<?>>) extensionClasses.get(extensionLoader);
                classes.stream()
                        .filter(it -> extensionFilter.test(it.getName()))
                        .flatMap(this::hierarchy)
                        .distinct()
                        .map(Class::getName)
                        .filter(classFilter)
                        .sorted()
                        .forEach(clazz -> {
                            final ClassReflectionModel model = new ClassReflectionModel();
                            model.setName(clazz);
                            model.setAllDeclaredConstructors(true);
                            model.setAllDeclaredMethods(true);
                            model.setAllDeclaredMethods(true);
                            context.register(model);
                        });
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Incompatible OpenWebBeans version", e);
            }

            // 7. producer types must be reflection friendly
            findProducedClasses(beans)
                    .map(Class::getName)
                    .sorted()
                    .forEach(name -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(name);
                        model.setAllDeclaredConstructors(true);
                        model.setAllDeclaredFields(true);
                        model.setAllDeclaredMethods(true);
                        context.register(model);
                    });

            // 8. enforce some build time init for annotations and some specific classes
            context.initializeAtBuildTime(
                    Reception.class.getName(),
                    TransactionPhase.class.getName(),
                    DefaultSingletonService.class.getName(),
                    WebBeansLoggerFacade.class.getName());
            try { // openwebbeans-slf4j is an optional module
                final Class<?> logger = context.loadClass("org.apache.openwebbeans.slf4j.Slf4jLogger");
                context.initializeAtBuildTime(logger.getName());
            } catch (final RuntimeException e) {
                // ignore, not there
            }

            // 9. we add the resource bundle + the bundle as resource for some extensions (thank you JUL)
            context.includeResourceBundle("openwebbeans/Messages");
            final ResourceModel resourceModel = new ResourceModel();
            resourceModel.setPattern("openwebbeans/Messages\\.properties");
            context.register(resourceModel);

            // 10. OWB creates proxies on TypeVariable (generics checks) so enable it
            final DynamicProxyModel typeVariableProxyModel = new DynamicProxyModel();
            typeVariableProxyModel.setClasses(singleton(TypeVariable.class.getName()));
            context.register(typeVariableProxyModel);

            // 11. interceptors
            context.findAnnotatedClasses(Interceptor.class).forEach(clazz -> {
                final ClassReflectionModel model = new ClassReflectionModel();
                model.setName(clazz.getName());
                model.setAllDeclaredConstructors(true);
                model.setAllDeclaredFields(true);
                model.setAllDeclaredMethods(true); // not sure it is that used but can be an injection point, todo: filter?
                context.register(model);
            });
            context.findAnnotatedClasses(InterceptorBinding.class).forEach(clazz -> {
                final ClassReflectionModel model = new ClassReflectionModel();
                model.setName(clazz.getName());
                model.setAllPublicMethods(true);
                context.register(model);
            });
        } finally {
            System.setProperties(original);
        }
    }

    private Stream<Class<?>> findProducedClasses(final Set<Bean<?>> beans) {
        return beans.stream()
                .filter(it -> AbstractProducerBean.class.isInstance(it) && BaseProducerProducer.class.isInstance(AbstractProducerBean.class.cast(it).getProducer()))
                .flatMap(it -> {
                    final BaseProducerProducer bpp = BaseProducerProducer.class.cast(AbstractProducerBean.class.cast(it).getProducer());
                    final Collection<Type> types = it.getTypes();
                    if (ProducerMethodProducer.class.isInstance(bpp)) {
                        return concat(types, get(bpp, "producerMethod", Method.class).getReturnType());
                    }
                    if (ProducerFieldProducer.class.isInstance(bpp)) {
                        return concat(types, get(bpp, "producerField", AnnotatedField.class).getJavaMember().getType());
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    private Stream<Class<?>> concat(final Collection<Type> types, final Class<?> type) {
        return Stream.concat(Stream.of(type), types.stream().filter(Class.class::isInstance).map(Class.class::cast))
                .distinct() // if types includes type, avoids to do twice the hierarchy
                .flatMap(this::hierarchy)
                .distinct();
    }

    private <T> T get(final BaseProducerProducer p, final String field, final Class<T> type) {
        try {
            final Field declaredField = p.getClass().getDeclaredField(field);
            if (!declaredField.isAccessible()) {
                declaredField.setAccessible(true);
            }
            return type.cast(declaredField.get(p));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Stream<? extends Class<?>> findServices(final Properties properties) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return properties.stringPropertyNames().stream()
                .filter(it -> it.startsWith("org.apache.webbeans.spi.") || it.equals(Filter.class.getName()))
                .map(properties::getProperty)
                .map(it -> {
                    try {
                        return loader.loadClass(it);
                    } catch (final ClassNotFoundException e) {
                        if (it.contains(".")) {
                            log.warn(e.getMessage(), e);
                        } // else can be "false" so just ignore
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    private String registerBeansForReflection(final Context context, final Set<Bean<?>> beans, final Predicate<String> classFilter) {
        final boolean includeClassResources = Boolean.parseBoolean(context.getProperty("extension.openwebbeans.classes.includeAsResources"));
        return beans.stream()
                .filter(it -> ManagedBean.class.isInstance(it) || CdiInterceptorBean.class.isInstance(it))
                .map(Bean::getBeanClass)
                .flatMap(this::hierarchy)
                .distinct()
                .map(Class::getName)
                .filter(classFilter)
                .sorted()
                .peek(clazz -> {
                    final ClassReflectionModel model = new ClassReflectionModel();
                    model.setName(clazz);
                    model.setAllDeclaredConstructors(true);
                    model.setAllDeclaredMethods(true);
                    model.setAllDeclaredFields(true);
                    context.register(model);

                    if (includeClassResources) {
                        final ResourceModel resourceModel = new ResourceModel();
                        resourceModel.setPattern(Pattern.quote(clazz.replace('.', '/') + ".class"));
                        context.register(resourceModel);
                    }
                })
                .collect(joining(","));
    }

    private void dumpProxies(final Context context, final WebBeansContext webBeansContext, final Set<Bean<?>> beans,
                             final Predicate<String> classFilter) {
        // interceptors/decorators
        beans.stream()
                .filter(InjectionTargetBean.class::isInstance)
                .map(InjectionTargetBean.class::cast)
                .forEach(InjectionTargetBean::defineInterceptorsIfNeeded);
        // normal scope
        beans.stream()
                .filter(it -> webBeansContext.getBeanManagerImpl().isNormalScope(it.getScope()))
                .forEach(webBeansContext.getNormalScopeProxyFactory()::createNormalScopeProxy);

        final Map<String, byte[]> proxies = getProxies(webBeansContext);
        log.debug("Proxies: {}", proxies.keySet());
        if (proxies.isEmpty()) {
            log.info("No proxy found for this application");
        } else {
            proxies.entrySet().stream()
                    .filter(it -> classFilter.test(it.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        context.registerGeneratedClass(e.getKey(), e.getValue());
                        log.info("Registered proxy '{}'", e.getKey());
                    });
        }
    }

    private Map<String, byte[]> getProxies(final WebBeansContext webBeansContext) {
        return ClassLoaderProxyService.Spy.class.cast(webBeansContext.getService(DefiningClassService.class)).getProxies();
    }

    private Stream<Class<?>> hierarchy(final Class<?> it) {
        return it == null || it == Object.class ?
                Stream.empty() :
                Stream.concat(
                        Stream.concat(Stream.of(it), hierarchy(it.getSuperclass())),
                        Stream.of(it.getInterfaces()).flatMap(this::hierarchy));
    }

    private Properties initProperties(final Context context, final OpenWebBeansConfiguration configuration,
                                      final String beanClassesList) {
        try {
            final Field field = OpenWebBeansConfiguration.class.getDeclaredField("configProperties");
            field.setAccessible(true);

            final Properties properties = Properties.class.cast(field.get(configuration));
            enrichProperties(properties, true);
            if (!Boolean.parseBoolean(context.getProperty("extension.openwebbeans.services.ignoreScannerService"))) {
                properties.put("org.apache.webbeans.spi.ScannerService", "org.apache.openwebbeans.se.PreScannedCDISeScannerService");
            }
            properties.putIfAbsent("org.apache.openwebbeans.se.PreScannedCDISeScannerService.classes", beanClassesList);

            if (!Boolean.parseBoolean(properties.getProperty("extension.openwebbeans.runtime.properties.skipOptimizations"))) {
                properties.putIfAbsent("org.apache.webbeans.spi.deployer.skipValidations", "true");
                properties.putIfAbsent("org.apache.webbeans.spi.deployer.skipVetoedOnPackages", "true");
            }

            final Predicate<String> droppedProperties = context.createPredicate("extension.openwebbeans.container.se.properties.runtime.excludes", EQUALS)
                    .orElseGet(() -> asList("configuration.ordinal", "org.apache.webbeans.lifecycle.standalone.fireApplicationScopeEvents")::contains);
            properties.stringPropertyNames().stream().filter(droppedProperties).forEach(properties::remove);
            final StringWriter writer = new StringWriter();
            try (final StringWriter w = writer) {
                properties.store(w, "Generated by Geronimo Arthur");
            }

            final Path workDir = Paths.get(requireNonNull(context.getProperty("workingDirectory"), "workingDirectory property"));
            context.addNativeImageOption("-H:OpenWebBeansProperties=" +
                    dump(workDir, "openwebbeans.properties", writer.toString().replaceAll("(?m)^#.*", "")));
            return properties;
        } catch (final Exception e) {
            throw new IllegalStateException("Incompatible OWB version", e);
        }
    }

    private void enrichProperties(final Properties properties, final boolean runtime) {
        properties.setProperty("configuration.ordinal", "10000");

        properties.setProperty("org.apache.webbeans.proxy.useStaticNames", "true");
        properties.setProperty("org.apache.webbeans.proxy.staticNames.useXxHash64", "true");

        properties.setProperty("org.apache.webbeans.spi.DefiningClassService", runtime ?
                "org.apache.webbeans.service.ClassLoaderProxyService$LoadOnly" :
                "org.apache.webbeans.service.ClassLoaderProxyService$Spy");
        properties.setProperty("org.apache.webbeans.spi.ApplicationBoundaryService",
                "org.apache.webbeans.corespi.se.SimpleApplicationBoundaryService");
    }

    private SeContainerInitializer configureInitializer(final Context context, final SeContainerInitializer initializer) {
        final Properties config = new Properties();
        enrichProperties(config, false); // before starting ensure we use a deterministic proxy generation config
        config.stringPropertyNames().forEach(k -> initializer.addProperty(k, config.getProperty(k)));

        if (Boolean.parseBoolean(context.getProperty("extension.openwebbeans.container.se.disableDiscovery"))) {
            initializer.disableDiscovery();
        }
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        initializer.setClassLoader(loader);
        ofNullable(context.getProperty("extension.openwebbeans.container.se.properties"))
                .ifPresent(props -> {
                    final Properties properties = readProps(props);
                    properties.stringPropertyNames().forEach(k -> initializer.addProperty(k, properties.getProperty(k)));
                });
        ofNullable(context.getProperty("extension.openwebbeans.container.se.services"))
                .ifPresent(props -> {
                    final Properties properties = readProps(props);
                    properties.stringPropertyNames().forEach(k -> {
                        try {
                            initializer.addProperty(k, loader.loadClass(properties.getProperty(k).trim()));
                        } catch (final ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        }
                    });
                });
        ofNullable(context.getProperty("extension.openwebbeans.container.se.classes"))
                .ifPresent(classes -> initializer.addBeanClasses(Stream.of(classes.split(","))
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .map(it -> {
                            try {
                                return loader.loadClass(it);
                            } catch (final ClassNotFoundException e) {
                                throw new IllegalArgumentException(e);
                            }
                        })
                        .toArray(Class<?>[]::new)));

        return initializer;
    }

    private Properties readProps(final String props) {
        final Properties properties = new Properties();
        try (final StringReader reader = new StringReader(props)) {
            properties.load(reader);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }

    private String dump(final Path workDir, final String name, final String value) {
        if (!java.nio.file.Files.isDirectory(workDir)) {
            try {
                java.nio.file.Files.createDirectories(workDir);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        final Path out = workDir.resolve(name);
        try {
            Files.write(
                    out, value.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        log.info("Created '{}'", out);
        return out.toAbsolutePath().toString();
    }
}

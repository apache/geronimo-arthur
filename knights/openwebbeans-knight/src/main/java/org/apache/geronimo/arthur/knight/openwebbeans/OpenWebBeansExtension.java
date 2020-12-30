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
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.openwebbeans.se.CDISeScannerService;
import org.apache.openwebbeans.se.PreScannedCDISeScannerService;
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
import org.apache.xbean.finder.filter.Filter;

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
import javax.enterprise.inject.spi.Bean;
import javax.inject.Qualifier;
import javax.interceptor.InterceptorBinding;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

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

            // 1. capture all proxies
            dumpProxies(context, webBeansContext, beans);

            // 2. register all classes which will require reflection + proxies
            final String beanClassesList = registerBeansForReflection(context, beans);
            getProxies(webBeansContext).keySet().forEach(name -> {
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
                    ClassLoaderProxyService.LoadFirst.class, StandaloneLifeCycle.class, StandaloneContextsService.class,
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
            // 4.3 annotations
            Stream.concat(Stream.concat(Stream.of(
                    Initialized.class, Destroyed.class, NormalScope.class, ApplicationScoped.class, Default.class,
                    Dependent.class, ConversationScoped.class, RequestScoped.class, Observes.class, ObservesAsync.class,
                    Qualifier.class, InterceptorBinding.class),
                    beanManager.getAdditionalQualifiers().stream()),
                    Stream.concat(
                            context.findAnnotatedClasses(Qualifier.class).stream(),
                            context.findAnnotatedClasses(NormalScope.class).stream()))
                    .distinct()
                    .forEach(clazz -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(clazz.getName());
                        model.setAllDeclaredMethods(true);
                        context.register(model);
                    });

            // enforce some build time init for annotations and some specific classes
            context.initializeAtBuildTime(
                    Reception.class.getName(),
                    TransactionPhase.class.getName(),
                    DefaultSingletonService.class.getName(),
                    WebBeansLoggerFacade.class.getName());

            // we add the resource bundle in the feature not here
        } finally {
            System.setProperties(original);
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

    private String registerBeansForReflection(final Context context, final Set<Bean<?>> beans) {
        final Predicate<String> classFilter = context.createIncludesExcludes("extension.openwebbeans.classes.filter.", PredicateType.STARTS_WITH);
        final boolean includeClassResources = Boolean.parseBoolean(context.getProperty("extension.openwebbeans.classes.includeAsResources"));
        return beans.stream()
                .filter(ManagedBean.class::isInstance)
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

    private void dumpProxies(final Context context, final WebBeansContext webBeansContext, final Set<Bean<?>> beans) {
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
            proxies.forEach((className, bytes) -> {
                context.registerGeneratedClass(className, bytes);
                log.info("Registered proxy '{}'", className);
            });
        }
    }

    private Map<String, byte[]> getProxies(final WebBeansContext webBeansContext) {
        return ClassLoaderProxyService.Spy.class.cast(webBeansContext.getService(DefiningClassService.class)).getProxies();
    }

    private Stream<Class<?>> hierarchy(final Class<?> it) {
        return it == null || it == Object.class ?
                Stream.empty() :
                Stream.concat(Stream.of(it), hierarchy(it.getSuperclass()));
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

            properties.remove("config.ordinal"); // no more needed since it will be unique
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
        properties.setProperty("config.ordinal", "10000");

        properties.setProperty("org.apache.webbeans.proxy.useStaticNames", "true");
        properties.setProperty("org.apache.webbeans.proxy.staticNames.useXxHash64", "true");

        properties.setProperty("org.apache.webbeans.spi.DefiningClassService", runtime ?
                "org.apache.webbeans.service.ClassLoaderProxyService$LoadFirst" :
                "org.apache.webbeans.service.ClassLoaderProxyService$Spy");
        properties.setProperty("org.apache.webbeans.spi.ApplicationBoundaryService",
                "org.apache.webbeans.corespi.se.SimpleApplicationBoundaryService");
    }

    private SeContainerInitializer configureInitializer(final Context context, final SeContainerInitializer initializer) {
        final Properties config = new Properties();
        enrichProperties(config, false); // before starting ensure we use a deterministic proxy generation config
        config.stringPropertyNames().forEach(k -> initializer.addProperty(k, config.getProperty(k)));

        if (Boolean.getBoolean(context.getProperty("extension.openwebbeans.container.se.disableDiscovery"))) {
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

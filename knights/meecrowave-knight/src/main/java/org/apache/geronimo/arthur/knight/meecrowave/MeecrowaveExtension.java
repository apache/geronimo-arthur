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
package org.apache.geronimo.arthur.knight.meecrowave;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.bus.extension.Extension;
import org.apache.cxf.bus.extension.TextExtensionFragmentParser;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.geronimo.arthur.knight.openwebbeans.OpenWebBeansExtension;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.meecrowave.runner.cli.CliOption;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.Filters;
import org.apache.xbean.finder.util.Files;

import javax.json.bind.annotation.JsonbProperty;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static java.util.Collections.list;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

@Slf4j
public class MeecrowaveExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        registerReflections(context);
        registerResources(context);
        registerIncludeResourceBundles(context);
        registerPerAnnotations(context);

        // run owb extension to ensure we get the beans and proxy registration
        final boolean skipDefaultExcludes = ofNullable(context.getProperty("meecrowave.extension.openwebbeans.skipDefaultExcludes"))
                .map(Boolean::parseBoolean)
                .orElse(false);
        new OpenWebBeansExtension().execute(skipDefaultExcludes ? context : context.wrap(context, (proxy, method, args) -> {
            if ("getProperty".equals(method.getName()) && "openwebbeans.extension.excludes".equals(args[0])) {
                final String existing = context.getProperty("openwebbeans.extension.excludes");
                return Stream.of(
                        existing,
                        "org.apache.cxf.Bus",
                        "org.apache.cxf.common.util.ClassUnwrapper",
                        "org.apache.cxf.interceptor.InterceptorProvider",
                        "org.apache.openwebbeans.se")
                        .filter(Objects::nonNull)
                        .collect(joining(","));
            }
            // else purely delegate to the actual context
            try {
                return method.invoke(context, args);
            } catch (final InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }));
    }

    private void registerPerAnnotations(final Context context) {
        // JSON-B models - we assume there is at least one prop with @JsonbProperty
        Stream.concat(
                context.findAnnotatedFields(JsonbProperty.class).stream()
                        .map(Field::getDeclaringClass),
                context.findAnnotatedMethods(JsonbProperty.class).stream()
                        .map(Method::getDeclaringClass))
                .distinct()
                .flatMap(it -> Stream.concat(Stream.of(it), context.findHierarchy(it)))
                .distinct()
                .map(c -> new ClassReflectionModel(c.getName(), true, null, true, true, null, null, true, true, null, null))
                .forEach(context::register);

        try { // meecrowave CLI if present
            context.loadClass("org.apache.commons.cli.CommandLineParser");
            context.findAnnotatedFields(CliOption.class).stream()
                    .map(Field::getDeclaringClass)
                    .map(c -> new ClassReflectionModel(c.getName(), null, null, null, null, null, null, true, null, null, null))
                    .forEach(context::register);
        } catch (final IllegalStateException ise) {
            // no-op, skip
        }
    }

    private void registerReflections(final Context context) {
        Stream.of("org.apache.cxf.BusFactory")
                .map(it -> new ClassReflectionModel(it, null, null, null, null, null, null, null, null, null, null))
                .forEach(context::register);

        findContextTypes(context)
                .map(it -> new ClassReflectionModel(it, null, null, null, true, null, null, null, null, null, null))
                .forEach(context::register);

        Stream.of(
                "org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor",
                "org.apache.cxf.cdi.DefaultApplication")
                .map(it -> new ClassReflectionModel(it, null, true, null, true, null, null, null, null, null, null))
                .forEach(context::register);

        Stream.concat(
                findExtensions(),
                Stream.of(
                        "org.apache.webbeans.web.lifecycle.WebContainerLifecycle",
                        "org.apache.meecrowave.logging.tomcat.LogFacade",
                        "org.apache.catalina.servlets.DefaultServlet",
                        "org.apache.catalina.authenticator.NonLoginAuthenticator"))
                .map(it -> new ClassReflectionModel(it, null, true, null, null, null, null, null, null, null, null))
                .forEach(context::register);

        Stream.of(
                "org.apache.cxf.transport.http.Headers",
                "org.apache.catalina.loader.WebappClassLoader",
                "org.apache.tomcat.util.descriptor.web.WebXml",
                "org.apache.coyote.http11.Http11NioProtocol",
                "javax.servlet.ServletContext")
                .map(it -> new ClassReflectionModel(it, null, null, null, true, null, null, null, null, null, null))
                .forEach(context::register);

        context.register(new ClassReflectionModel(
                "org.apache.cxf.jaxrs.provider.ProviderFactory", null, null, null, null, null, null, null, null, null,
                singletonList(new ClassReflectionModel.MethodReflectionModel("getReadersWriters", null))));
        context.register(new ClassReflectionModel(
                "org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider$ProvidedInstance", null, null, null, null, null, null, null, null,
                singletonList(new ClassReflectionModel.FieldReflectionModel("instance", null)), null));
        context.register(new ClassReflectionModel(
                "org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider", null, null, null, null, null, null, null, null,
                singletonList(new ClassReflectionModel.FieldReflectionModel("providers", null)), null));
        context.register(new ClassReflectionModel(
                "org.apache.xbean.finder.AnnotationFinder", null, null, null, null, null, null, null, null,
                singletonList(new ClassReflectionModel.FieldReflectionModel("linking", true)), null));
    }

    private Stream<String> findExtensions() {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            final Enumeration<URL> resources = loader.getResources("META-INF/cxf/bus-extensions.txt");
            return list(resources).stream()
                    .flatMap(url -> new TextExtensionFragmentParser(loader).getExtensions(url).stream())
                    .map(Extension::getClassname)
                    .distinct();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Stream<String> findContextTypes(final Context context) {
        return InjectionUtils.STANDARD_CONTEXT_CLASSES.stream().filter(it -> {
            try {
                return context.loadClass(it) != null;
            } catch (final IllegalStateException ise) {
                return false;
            }
        });
    }

    private void registerResources(final Context context) {
        Stream.of(
                "org\\/apache\\/catalina\\/.*\\.properties", // can be refined to not include resource bundles
                "(javax|jakarta)\\/servlet\\/(jsp\\/)?resources\\/.*\\.(xsd|dtd)",
                "meecrowave\\.properties",
                "META-INF/cxf/bus-extensions\\.txt",
                "org/apache/cxf/version/version\\.properties")
                .map(ResourceModel::new)
                .forEach(context::register);
    }

    private void registerIncludeResourceBundles(final Context context) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            final Filter includedJarNamePrefixes = Filters.prefixes("cxf-", "tomcat-");
            Stream.concat(
                    Stream.of( // spec, we hardcode them since it is unlikely to move - for now javax ones but jakarta would be similar
                            "javax.servlet.LocalStrings",
                            "javax.servlet.http.LocalStrings"),
                    new UrlSet(loader)
                            .exclude(loader.getParent())
                            .excludeJvm()
                            .getUrls().stream()
                            .map(Files::toFile)
                            .filter(jar -> includedJarNamePrefixes.accept(jar.getName()))
                            .flatMap(this::extractBundles))
                    .map(ResourceBundleModel::new)
                    .forEach(context::register);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Stream<String> extractBundles(final File file) {
        if (file.getName().startsWith("cxf-")) {
            return propertiesFiles(file, "Messages");
        } // else tomcat
        return propertiesFiles(file, "LocalStrings");
    }

    private Stream<String> propertiesFiles(final File file, final String name) {
        final String suffix = '/' + name + ".properties";
        try (final JarFile jar = new JarFile(file)) {
            return list(jar.entries()).stream()
                    .filter(it -> !it.isDirectory())
                    .map(JarEntry::getName)
                    .filter(n -> n.endsWith(suffix))
                    .map(n -> n.replace(".", "/").substring(0, n.length() - ".properties".length()));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

}

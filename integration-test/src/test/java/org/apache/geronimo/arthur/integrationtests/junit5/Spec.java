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
package org.apache.geronimo.arthur.integrationtests.junit5;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.geronimo.arthur.integrationtests.junit5.Spec.ExpectedType.EQUALS;
import static org.apache.ziplock.JarLocation.jarFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.integrationtests.container.MavenContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.AnnotationUtils;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Target(METHOD)
@Retention(RUNTIME)
@ExtendWith(Spec.Impl.class)
public @interface Spec {
    String project() default "";

    String binary() default "./target/${project.artifactId}.graal.bin";

    int exitCode() default 0;

    String expectedOutput() default "";

    ExpectedType expectedType() default EQUALS;

    String[] forwardedExecutionSystemProperties() default {};

    enum ExpectedType {
        EQUALS(Assertions::assertEquals),
        EQUALS_TRIMMED((a, b) -> assertEquals(a, b.trim(), b)),
        MATCHES((a, b) -> assertTrue(a.matches(b), b));

        private final BiConsumer<String, String> assertFn;

        ExpectedType(final BiConsumer<String, String> assertFn) {
            this.assertFn = assertFn;
        }
    }

    @Slf4j // todo: make it parallelisable?
    class Impl implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        public static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Impl.class);

        @Override
        public void beforeEach(final ExtensionContext context) throws Exception {
            final Method method = context.getRequiredTestMethod();
            final Optional<Spec> specOpt = AnnotationUtils.findAnnotation(method, Spec.class);
            if (!specOpt.isPresent()) {
                return;
            }

            final MavenContainer mvn = findContainer(context);
            final Spec spec = specOpt.orElseThrow(IllegalStateException::new);

            final ExtensionContext.Store store = context.getStore(NAMESPACE);
            store.put(Spec.class, spec);
            store.put(MavenContainer.class, mvn);
            final Invocation invocation = () -> {
                final String project = of(spec.project())
                        .filter(it -> !it.isEmpty())
                        .orElseGet(() -> "integration-tests/" + context.getRequiredTestMethod().getName());
                final Path root = jarFromResource(project).toPath().resolve(project);
                final Collection<String> files = copyProject(mvn, root, spec);
                store.put(CopiedFiles.class, new CopiedFiles(mvn, files));

                log.info("Compiling the project '" + project.substring(project.lastIndexOf('/') + 1) + "'");
                final ExecResult result = buildAndRun(
                        mvn, spec.binary().replace("${project.artifactId}", findArtifactId(root.resolve("pom.xml"))),
                        spec.forwardedExecutionSystemProperties());
                store.put(ExecResult.class, result);
                assertEquals(spec.exitCode(), result.getExitCode(), () -> result.getStdout() + result.getStderr());
                spec.expectedType().assertFn.accept(
                        spec.expectedOutput(),
                        String.join("\n", result.getStdout(), result.getStderr()).trim());
            };

            if (Stream.of(method.getParameterTypes()).noneMatch(it -> it == Invocation.class)) {
                invocation.run();
            } else { // the test calls it itself since it requires some custom init/destroy
                store.put(Invocation.class, invocation);
            }
        }

        @Override
        public void afterEach(final ExtensionContext context) {
            final Optional<CopiedFiles> copiedFiles = ofNullable(context.getStore(NAMESPACE).get(CopiedFiles.class, CopiedFiles.class));
            assertTrue(copiedFiles.isPresent(), "Maven build not executed");
            copiedFiles
                    .filter(f -> !f.files.isEmpty())
                    .ifPresent(this::cleanFolder);
        }

        private String findArtifactId(final Path pom) {
            try {
                final String start = "  <artifactId>";
                return Files.lines(pom)
                        .filter(it -> it.startsWith(start))
                        .map(it -> it.substring(it.indexOf(start) + start.length(), it.indexOf('<', start.length() + 1)))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No artifactId found in " + pom));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private void cleanFolder(final CopiedFiles files) {
            try {
                files.mvn.execInContainer(Stream.concat(
                        Stream.of("rm", "-Rf", "target"),
                        files.files.stream().map(it -> it.replace("\"", "\\\""))
                ).toArray(String[]::new));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private MavenContainer findContainer(ExtensionContext context) throws IllegalAccessException {
            final Object instance = context.getRequiredTestInstance();
            final Field containerField = AnnotationUtils.findAnnotatedFields(instance.getClass(), Container.class, i -> true).stream()
                    .filter(it -> MavenContainer.class == it.getType() && Modifier.isStatic(it.getModifiers()))
                    .findFirst()
                    .orElseThrow(IllegalStateException::new);
            if (!containerField.isAccessible()) {
                containerField.setAccessible(true);
            }
            return MavenContainer.class.cast(containerField.get(null));
        }

        private Collection<String> copyProject(final MavenContainer mvn, final Path root, final Spec spec) {
            final Collection<String> files = new ArrayList<>();
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final String target = Paths.get(requireNonNull(mvn.getWorkingDirectory(), "mvn workdir is null"))
                                .resolve(root.relativize(file)).toString();
                        mvn.copyFileToContainer(
                                MountableFile.forHostPath(file),
                                target);
                        files.add(target);
                        log.debug("Copied '{}' to container '{}'", file, target);
                        return super.visitFile(file, attrs);
                    }
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return files;
        }

        private ExecResult buildAndRun(final MavenContainer mvn, final String binary,
                                       final String[] systemProps) {
            try {
                final ExecResult build = mvn.execInContainer("mvn", "-e", "package", "arthur:native-image");
                if (log.isDebugEnabled()) {
                    log.debug("Exit status: {}, Output:\n{}", build.getExitCode(), toMvnOutput(build));
                }
                assertEquals(0, build.getExitCode(), () -> toMvnOutput(build));

                final String[] command = Stream.concat(
                        Stream.of(binary),
                        Stream.of(systemProps).map(it -> "-D" + it + '=' + lookupSystemProperty(it)
                                .replace("$JAVA_HOME", "/usr/local/openjdk-8")))
                        .toArray(String[]::new);
                return mvn.execInContainer(command);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ie);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private String lookupSystemProperty(final String it) {
            switch (it) {
                case "java.library.path":
                    return "$JAVA_HOME/jre/lib/amd64";
                case "javax.net.ssl.trustStore":
                    return "$JAVA_HOME/jre/lib/security/cacerts";
                default:
                    return System.getProperty(it);
            }
        }

        private String toMvnOutput(final ExecResult mvnResult) {
            return Stream.of(mvnResult.getStdout(), mvnResult.getStderr())
                    .map(it -> it
                            // workaround an issue with mvn/slf4j output through testcontainers
                            .replace("\n", "")
                            .replace("[INFO] ", "\n[INFO] ")
                            .replace("[WARNING] ", "\n[WARNING] ")
                            .replace("[ERROR] ", "\n[ERROR] ")
                            .replace("    at", "\n    at")
                            .replace("Caused by:", "\nCaused by:")
                            .replace("ms[", "ms\n["))
                    .collect(joining("\n"));
        }

        @Override
        public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            return resolveParameter(parameterContext, extensionContext) != null;
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            final Class<?> type = parameterContext.getParameter().getType();
            return extensionContext.getStore(NAMESPACE).get(type, type);
        }

        @RequiredArgsConstructor
        private static final class CopiedFiles {
            private final MavenContainer mvn;
            private final Collection<String> files;
        }
    }
}

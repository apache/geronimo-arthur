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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeImageMojoTest {
    @TempDir
    Path directory;

    @Test
    void projectVersionsWinOverExtensionVersions() throws Exception {
        final Artifact owb = artifact("openwebbeans-impl", "2.0.28", "jar", null, "compile");
        final Artifact xbean = artifact("xbean-finder-shaded", "4.31", "jar", null, "runtime");
        final NativeImageMojo mojo = mojo(asList(owb, xbean), asList(
                "test:openwebbeans-impl:2.0.26?transitive=false",
                "test:xbean-finder-shaded:4.23?transitive=false",
                "test:knight:1.0?transitive=false"));

        assertEquals(asList("application:1", "openwebbeans-impl:2.0.28", "xbean-finder-shaded:4.31", "knight:1.0"), coordinates(mojo));
    }

    @Test
    void projectSelectionIsNotReplacedByAHigherExtensionVersion() throws Exception {
        final NativeImageMojo mojo = mojo(singletonList(artifact("library", "1", "jar", null, "compile")),
                singletonList("test:library:2?transitive=false"));
        assertEquals(asList("application:1", "library:1"), coordinates(mojo));
    }

    @Test
    void firstExtensionWinsAndClassifiersAndTypesRemainDistinct() throws Exception {
        final NativeImageMojo mojo = mojo(asList(
                artifact("library", "1", "jar", null, "compile"),
                artifact("library", "1", "zip", null, "compile")), asList(
                "test:library:jar:jakarta:2?transitive=false",
                "test:extension-only:1?transitive=false",
                "test:extension-only:2?transitive=false"));
        assertEquals(asList("application:1", "library:1", "library:1", "library:2", "extension-only:1"), coordinates(mojo));
        assertEquals(asList("", "", "", "jakarta", ""), entries(mojo).stream()
                .map(e -> e.getKey().hasClassifier() ? e.getKey().getClassifier() : "").collect(toList()));
    }

    @Test
    void onlyEligibleProjectDependenciesTakePrecedence() throws Exception {
        final NativeImageMojo mojo = mojo(singletonList(artifact("library", "2", "jar", null, "test")),
                singletonList("test:library:1?transitive=false"));
        assertEquals(asList("application:1", "library:1"), coordinates(mojo));
        set(mojo, "supportTestArtifacts", true);
        assertEquals(asList("application:1", "application:1", "library:2"), coordinates(mojo));
    }

    @Test
    void missingProjectFilesDoNotHideExtensionDependencies() throws Exception {
        final Artifact missing = artifact("library", "2", "jar", null, "compile");
        Files.delete(missing.getFile().toPath());
        final NativeImageMojo mojo = mojo(singletonList(missing), singletonList("test:library:1?transitive=false"));
        assertEquals(asList("application:1", "library:1"), coordinates(mojo));
    }

    private NativeImageMojo mojo(final List<Artifact> dependencies, final List<String> extensions) throws Exception {
        final NativeImageMojo mojo = new NativeImageMojo() {
            @Override
            protected org.eclipse.aether.artifact.Artifact resolve(final org.eclipse.aether.artifact.Artifact artifact) {
                try {
                    final Path file = directory.resolve(artifact.getArtifactId() + '-' + artifact.getVersion() + '-' + artifact.getClassifier() + '.' + artifact.getExtension());
                    if (!Files.exists(file)) {
                        Files.createFile(file);
                    }
                    return artifact.setFile(file.toFile());
                } catch (final java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        mojo.project = new MavenProject();
        mojo.project.setArtifacts(new LinkedHashSet<>(dependencies));
        set(mojo, "groupId", "test");
        set(mojo, "artifactId", "application");
        set(mojo, "version", "1");
        set(mojo, "packaging", "jar");
        set(mojo, "classes", Files.createDirectories(directory.resolve("classes")).toFile());
        set(mojo, "testClasses", Files.createDirectories(directory.resolve("test-classes")).toFile());
        set(mojo, "excludedArtifacts", emptyList());
        set(mojo, "supportedTypes", asList("jar", "zip"));
        set(mojo, "graalExtensions", extensions);
        return mojo;
    }

    private Artifact artifact(final String id, final String version, final String type, final String classifier, final String scope) throws Exception {
        final Artifact artifact = new DefaultArtifact("test", id, version, scope, type, classifier, new DefaultArtifactHandler(type));
        artifact.setFile(Files.createFile(directory.resolve(id + '-' + version + '-' + classifier + '.' + type)).toFile());
        return artifact;
    }

    private List<String> coordinates(final NativeImageMojo mojo) throws Exception {
        return entries(mojo).stream().map(e -> e.getKey().getArtifactId() + ':' + e.getKey().getVersion()).collect(toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map.Entry<Artifact, Path>> entries(final NativeImageMojo mojo) throws Exception {
        final java.lang.reflect.Method method = NativeImageMojo.class.getDeclaredMethod("findClasspathFiles");
        method.setAccessible(true);
        try (Stream<Map.Entry<Artifact, Path>> entries = (Stream<Map.Entry<Artifact, Path>>) method.invoke(mojo)) {
            return entries.collect(toList());
        }
    }

    private void set(final NativeImageMojo mojo, final String name, final Object value) throws Exception {
        final Field field = NativeImageMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}

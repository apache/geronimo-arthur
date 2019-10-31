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
package org.apache.geronimo.arthur.impl.nativeimage.generator.extension;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.PropertyOrderStrategy;

import org.apache.geronimo.arthur.api.RegisterClass;
import org.apache.geronimo.arthur.api.RegisterField;
import org.apache.geronimo.arthur.api.RegisterMethod;
import org.apache.geronimo.arthur.api.RegisterResource;
import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.impl.nativeimage.generator.DefautContext;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.ClassesArchive;
import org.junit.jupiter.api.Test;

class AnnotationExtensionTest {
    @Test
    void scan() throws Exception {
        final ClassesArchive archive = new ClassesArchive(AnnotationExtensionTest.class.getClasses());
        final AnnotationFinder finder = new AnnotationFinder(archive);
        final DefautContext context = new DefautContext(new ArthurNativeImageConfiguration(),
                finder::findAnnotatedClasses,
                finder::findAnnotatedMethods,
                p -> Collection.class.cast(finder.findImplementations(p)));
        new AnnotationExtension().execute(context);
        try (final Jsonb jsonb = JsonbBuilder.create(
                new JsonbConfig().withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL))) {
            assertTrue(context.isModified());
            {
                final Iterator<ClassReflectionModel> reflections = context.getReflections().stream()
                        .sorted(comparing(ClassReflectionModel::getName))
                        .collect(toList())
                        .iterator();

                assertTrue(reflections.hasNext());
                assertEquals("{\"allDeclaredClasses\":true,\"allDeclaredConstructors\":true,\"allDeclaredFields\":true,\"allDeclaredMethods\":true,\"name\":\"" + All.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"allDeclaredFields\":true,\"name\":\"" + AllFields.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"name\":\"" + Child1.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"name\":\"" + Child2.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"name\":\"" + ChildRegistersIt.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"fields\":[{\"name\":\"name\"}],\"name\":\"" + ExplicitFields.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"methods\":[{\"name\":\"hasExplicitMethod\",\"parameterTypes\":[]}],\"name\":\"" + ExplicitMethod.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertTrue(reflections.hasNext());
                assertEquals("{\"name\":\"" + JustTheClass.class.getName() + "\"}", jsonb.toJson(reflections.next()));

                assertFalse(reflections.hasNext());
            }
            assertEquals("myres1,myres2", context.getResources().stream().map(ResourceModel::getPattern).sorted().collect(joining(",")));
            assertEquals("another,org.bundle1,org.foo.2", context.getBundles().stream().map(ResourceBundleModel::getName).sorted().collect(joining(",")));
        }
    }

    @RegisterResource(
            patterns = {"myres1", "myres2"},
            bundles = {"org.bundle1", "org.foo.2", "another"}
    )
    public static class App {
    }

    public static class IgnoredNormally {
        private String name;

        public String isIgnored() {
            return "test";
        }
    }

    public static class ChildRegistersIt {
    }

    @RegisterClass
    public static class Child1 extends ChildRegistersIt {
    }

    @RegisterClass
    public static class Child2 extends ChildRegistersIt {
    }

    @RegisterClass
    public static class JustTheClass {
        private String name;

        public String isJust() {
            return "test";
        }
    }

    @RegisterClass(all = true)
    public static class All {
        private String name;

        public All() {
            // no-op
        }

        public All(final String name) {
            this.name = name;
        }

        public String isAll() {
            return "test";
        }
    }

    @RegisterClass(allDeclaredFields = true)
    public static class AllFields {
        private String name;

        public String allF() {
            return "test";
        }
    }

    @RegisterClass
    public static class ExplicitFields {
        @RegisterField
        private String name;

        public String hasExplicitField() {
            return "test";
        }
    }

    @RegisterClass
    public static class ExplicitMethod {
        private String name;

        @RegisterMethod
        public String hasExplicitMethod() {
            return "test";
        }
    }
}

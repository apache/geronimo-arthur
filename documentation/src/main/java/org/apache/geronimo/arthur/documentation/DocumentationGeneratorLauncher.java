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
package org.apache.geronimo.arthur.documentation;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;

import java.beans.PropertyEditorManager;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.documentation.editor.PathEditor;
import org.apache.geronimo.arthur.documentation.io.ConsumedPrintStream;
import org.apache.geronimo.arthur.documentation.io.FolderVisitor;
import org.apache.geronimo.arthur.documentation.lang.PathPredicates;
import org.apache.geronimo.arthur.documentation.mojo.MojoParser;
import org.apache.geronimo.arthur.documentation.renderer.AsciidocRenderer;
import org.tomitribe.crest.Main;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = PRIVATE)
public final class DocumentationGeneratorLauncher {
    public static void main(final String[] args) throws Exception {
        final Map<Class<?>, Object> services = createServices().collect(toMap(Object::getClass, identity()));
        final SystemEnvironment env = new SystemEnvironment(services) {
            private final ConsumedPrintStream err = new ConsumedPrintStream(log::error);
            private final ConsumedPrintStream out = new ConsumedPrintStream(log::info);

            @Override
            public PrintStream getOutput() {
                return out;
            }

            @Override
            public PrintStream getError() {
                return err;
            }
        };
        PropertyEditorManager.registerEditor(Path.class, PathEditor.class);
        Environment.ENVIRONMENT_THREAD_LOCAL.set(env);
        try {
            new Main().main(env, args);
        } finally {
            // cheap cleanup solution
            services.values().stream()
                    .filter(AutoCloseable.class::isInstance)
                    .map(AutoCloseable.class::cast)
                    .forEach(it -> {
                        try {
                            it.close();
                        } catch (final Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    });
            Environment.ENVIRONMENT_THREAD_LOCAL.remove();
        }
    }

    private static Stream<Object> createServices() {
        final PathPredicates predicates = new PathPredicates();
        return Stream.of(predicates, new AsciidocRenderer(), new FolderVisitor(predicates), new MojoParser());
    }
}

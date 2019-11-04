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
package org.apache.geronimo.arthur.knight.jsch;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JschExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Function<String, Class<?>> load = name -> {
            try {
                return loader.loadClass(name);
            } catch (final ClassNotFoundException e) {
                return null;
            }
        };

        try {
            final Class<?> jsch = load.apply("com.jcraft.jsch.JSch");
            if (jsch == null) {
                log.info("JSch no available, skipping");
                return;
            }

            final Field config = jsch.getDeclaredField("config");
            if (!config.isAccessible()) {
                config.setAccessible(true);
            }
            final Collection<String> values = Hashtable.class.cast(config.get(null)).values();

            values.stream()
                    .filter(it -> !"com.jcraft.jsch.jcraft.Compression".equalsIgnoreCase(it)) // requires other libs
                    .map(load)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(clazz -> {
                        final ClassReflectionModel model = new ClassReflectionModel();
                        model.setName(clazz.getName());
                        model.setAllDeclaredConstructors(true);
                        return model;
                    })
                    .forEach(context::register);

            context.enableAllSecurityServices();

            context.initializeAtBuildTime(
                    Stream.of(jsch.getName(), jsch.getName() + "$1") // JSch.DEVNULL
                            .filter(it -> load.apply(it) != null) // tolerate jsch extraction of DEVNULL in a root class
                    .toArray(String[]::new));
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

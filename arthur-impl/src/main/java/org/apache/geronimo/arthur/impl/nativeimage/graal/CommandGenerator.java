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
package org.apache.geronimo.arthur.impl.nativeimage.graal;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;

public class CommandGenerator {
    public List<String> generate(final ArthurNativeImageConfiguration configuration) {
        return Stream.of(ArthurNativeImageConfiguration.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ArthurNativeImageConfiguration.GraalCommandPart.class))
                .sorted(comparing(field -> field.getAnnotation(ArthurNativeImageConfiguration.GraalCommandPart.class).order()))
                .flatMap(field -> {
                    final ArthurNativeImageConfiguration.GraalCommandPart config = field.getAnnotation(ArthurNativeImageConfiguration.GraalCommandPart.class);
                    final Type genericType = field.getGenericType();
                    final Object instance = ofNullable(get(field, configuration))
                            .orElseGet(() -> getDefaultValueFor(field, configuration));
                    return Stream.concat(Stream.of(config.preParts()), toCommand(field, config, genericType, instance));
                })
                .collect(toList());
    }

    private Object getDefaultValueFor(final Field field, final ArthurNativeImageConfiguration config) {
        switch (field.getName()) {
            case "main":
                return "main";
            case "output":
                return ofNullable(config.getMain()).orElse("main").replace("$", "_") + ".graal.exec";
            default:
                return null;
        }
    }

    private Stream<? extends String> toCommand(final Field field,
                                               final ArthurNativeImageConfiguration.GraalCommandPart config,
                                               final Type genericType,
                                               final Object instance) {
        if (config.passthrough()) {
            if (genericType == String.class) {
                if (instance == null) {
                    return Stream.empty();
                }
                return Stream.of(instance.toString());
            } else if (isCollectionOfString(genericType)) {
                if (instance == null || Collection.class.cast(instance).isEmpty()) {
                    return Stream.empty();
                }
                return ((Collection<String>) instance).stream();
            }
            throw new IllegalArgumentException("@GraalCommandPart(passthrough=true) not supported for " + field);
        }

        if (isCollectionOfString(genericType)) {
            if (instance == null || Collection.class.cast(instance).isEmpty()) {
                return Stream.empty();
            }
            final Collection<String> strings = (Collection<String>) instance;
            return Stream.of(String.format(
                    config.template(),
                    String.join(
                            config.joiner().replace("${File.pathSeparator}", File.pathSeparator),
                            strings)));
        }

        // else assume "primitive"
        if (instance != null && !Boolean.FALSE.equals(instance) /*we skip disabled commands*/) {
            return Stream.of(String.format(config.template(), instance));
        }
        return Stream.empty();
    }

    private boolean isCollectionOfString(final Type genericType) {
        final boolean isPt = ParameterizedType.class.isInstance(genericType);
        if (isPt) {
            final ParameterizedType parameterizedType = ParameterizedType.class.cast(genericType);
            return Class.class.isInstance(parameterizedType.getRawType()) &&
                    Collection.class.isAssignableFrom(Class.class.cast(parameterizedType.getRawType())) &&
                    parameterizedType.getActualTypeArguments().length == 1 &&
                    String.class == parameterizedType.getActualTypeArguments()[0];
        }
        return false;
    }

    private Object get(final Field field, final ArthurNativeImageConfiguration configuration) {
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        try {
            return field.get(configuration);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

import org.apache.geronimo.arthur.api.RegisterClass;
import org.apache.geronimo.arthur.api.RegisterClasses;
import org.apache.geronimo.arthur.api.RegisterField;
import org.apache.geronimo.arthur.api.RegisterMethod;
import org.apache.geronimo.arthur.api.RegisterResource;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class AnnotationExtension implements ArthurExtension {
    @Override
    public int order() {
        return 100;
    }

    @Override
    public void execute(final Context context) {
        context.findAnnotatedClasses(RegisterClass.class).stream()
                .flatMap(clazz -> register(clazz, clazz.getAnnotation(RegisterClass.class)))
                .forEach(context::register);

        final Collection<RegisterResource> resources = context.findAnnotatedClasses(RegisterResource.class).stream()
                .flatMap(clazz -> Stream.of(clazz.getAnnotation(RegisterResource.class)))
                .collect(toList());
        resources.stream()
                .flatMap(rr -> Stream.of(rr.patterns()))
                .map(pattern -> {
                    final ResourceModel resourceModel = new ResourceModel();
                    resourceModel.setPattern(pattern);
                    return resourceModel;
                })
                .distinct()
                .forEach(context::register);
        resources.stream()
                .flatMap(rr -> Stream.of(rr.bundles()))
                .map(name -> {
                    final ResourceBundleModel bundleModel = new ResourceBundleModel();
                    bundleModel.setName(name);
                    return bundleModel;
                })
                .distinct()
                .forEach(context::register);
        context.findAnnotatedClasses(RegisterClasses.Entry.class).stream()
                .map(it -> it.getAnnotation(RegisterClasses.Entry.class))
                .flatMap(entry -> doRegisterEntry(context, entry))
                .forEach(context::register);
        context.findAnnotatedClasses(RegisterClasses.class).stream()
                .flatMap(it -> Stream.of(it.getAnnotation(RegisterClasses.class).value()))
                .flatMap(entry -> doRegisterEntry(context, entry))
                .forEach(context::register);
    }

    private Stream<ClassReflectionModel> doRegisterEntry(final Context context, final RegisterClasses.Entry entry) {
        try {
            return register(!entry.className().isEmpty() ? context.loadClass(entry.className()) : entry.clazz(), entry.registration());
        } catch (final IllegalStateException ise) {
            // class not loadable, ignore
            return Stream.empty();
        }
    }

    private Stream<ClassReflectionModel> register(final Class<?> clazz, final RegisterClass config) {
        final ClassReflectionModel reflectionModel = new ClassReflectionModel();
        reflectionModel.setName(clazz.getName());
        if (config.all()) {
            reflectionModel.setAllDeclaredClasses(true);
            reflectionModel.setAllDeclaredConstructors(true);
            reflectionModel.setAllDeclaredMethods(true);
            reflectionModel.setAllDeclaredFields(true);
        } else {
            if (config.allDeclaredClasses()) {
                reflectionModel.setAllDeclaredClasses(true);
            }
            if (config.allDeclaredConstructors()) {
                reflectionModel.setAllDeclaredConstructors(true);
            }
            if (config.allDeclaredMethods()) {
                reflectionModel.setAllDeclaredMethods(true);
            }
            if (config.allPublicClasses()) {
                reflectionModel.setAllPublicClasses(true);
            }
            if (config.allPublicConstructors()) {
                reflectionModel.setAllPublicConstructors(true);
            }
            if (config.allPublicMethods()) {
                reflectionModel.setAllPublicMethods(true);
            }
            if (config.allDeclaredFields()) {
                reflectionModel.setAllDeclaredFields(true);
            }
            if (config.allPublicFields()) {
                reflectionModel.setAllPublicFields(true);
            }
        }

        final List<ClassReflectionModel.FieldReflectionModel> registeredFields = Stream.of(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(RegisterField.class))
                .map(field -> {
                    final ClassReflectionModel.FieldReflectionModel fieldReflectionModel = new ClassReflectionModel.FieldReflectionModel();
                    fieldReflectionModel.setName(field.getName());
                    if (field.getAnnotation(RegisterField.class).allowWrite()) {
                        fieldReflectionModel.setAllowWrite(true);
                    }
                    return fieldReflectionModel;
                })
                .collect(toList());
        if ((config.allDeclaredFields() || config.allPublicFields()) && !registeredFields.isEmpty()) {
            throw new IllegalArgumentException("Don't use allDeclaredFields and allPublicFields with @RegisterField: " + clazz);
        } else if (!registeredFields.isEmpty()) {
            reflectionModel.setFields(registeredFields);
        }

        final List<ClassReflectionModel.MethodReflectionModel> registeredMethods = Stream.of(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RegisterMethod.class))
                .map(field -> {
                    final ClassReflectionModel.MethodReflectionModel methodReflectionModel = new ClassReflectionModel.MethodReflectionModel();
                    methodReflectionModel.setName(field.getName());
                    methodReflectionModel.setParameterTypes(asList(field.getParameterTypes()));
                    return methodReflectionModel;
                })
                .collect(toList());
        if ((config.allDeclaredMethods() || config.allPublicMethods()) && !registeredMethods.isEmpty()) {
            throw new IllegalArgumentException("Don't use allDeclaredFields and allDeclaredMethods with @RegisterMethod: " + clazz);
        } else if (!registeredMethods.isEmpty()) {
            reflectionModel.setMethods(registeredMethods);
        }

        final Stream<ClassReflectionModel> model = Stream.of(reflectionModel);
        final Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class && superclass != clazz) {
            return Stream.concat(
                    register(superclass, ofNullable(superclass.getAnnotation(RegisterClass.class)).orElse(config)),
                    model);
        }

        return model;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj != null && AnnotationExtension.class == obj.getClass();
    }
}

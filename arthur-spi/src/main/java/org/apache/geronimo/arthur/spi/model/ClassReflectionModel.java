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
package org.apache.geronimo.arthur.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassReflectionModel {
    private String name;
    private Condition condition; // >= GraalVM 22.2
    private Boolean allDeclaredConstructors;
    private Boolean allPublicConstructors;
    private Boolean allDeclaredMethods;
    private Boolean allPublicMethods;
    private Boolean allDeclaredClasses;
    private Boolean allPublicClasses;
    private Boolean allDeclaredFields;
    private Boolean allPublicFields;
    private Boolean queryAllDeclaredMethods;
    private Boolean queryAllDeclaredConstructors;
    private Boolean queryAllPublicMethods;
    private Boolean queryAllPublicConstructors;
    private Boolean unsafeAllocated;
    private Collection<FieldReflectionModel> fields;
    private Collection<MethodReflectionModel> methods;
    private Collection<MethodReflectionModel> queriedMethods;

    public ClassReflectionModel(final String name,
                                final Boolean allDeclaredConstructors, final Boolean allPublicConstructors,
                                final Boolean allDeclaredMethods, final Boolean allPublicMethods,
                                final Boolean allDeclaredClasses, final Boolean allPublicClasses,
                                final Boolean allDeclaredFields, final Boolean allPublicFields,
                                final Collection<FieldReflectionModel> fields, final Collection<MethodReflectionModel> methods) {
        this(name, null, allDeclaredConstructors, allPublicConstructors, allDeclaredMethods, allPublicMethods, allDeclaredClasses, allPublicClasses, allDeclaredFields, allPublicFields, null, null, null, null, null, fields, methods, null);
    }

    public ClassReflectionModel allPublic(final String name) {
        return new ClassReflectionModel(name, null, null, true, null, true, null, null, null, true, null, null, null, null, null, null, null, null);
    }

    public ClassReflectionModel allPublicConstructors(final String name) {
        return new ClassReflectionModel(name, null, null, true, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ClassReflectionModel allDeclaredConstructors(final String name) {
        return new ClassReflectionModel(name, null, null, null, true, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ClassReflectionModel allDeclared(final String name) {
        return new ClassReflectionModel(name, null, true, null, true, null, null, null, true, null, null, null, null, null, null, null, null, null);
    }

    public void merge(final ClassReflectionModel other) {
        if (other.getAllDeclaredClasses() != null && other.getAllDeclaredClasses()) {
            setAllDeclaredClasses(true);
        }
        if (other.getAllDeclaredFields() != null && other.getAllDeclaredFields()) {
            setAllDeclaredFields(true);
        }
        if (other.getAllDeclaredConstructors() != null && other.getAllDeclaredConstructors()) {
            setAllDeclaredConstructors(true);
        }
        if (other.getAllDeclaredMethods() != null && other.getAllDeclaredMethods()) {
            setAllDeclaredMethods(true);
        }
        if (other.getAllPublicMethods() != null && other.getAllPublicMethods()) {
            setAllPublicMethods(true);
        }
        if (other.getAllPublicFields() != null && other.getAllPublicFields()) {
            setAllPublicFields(true);
        }
        if (other.getAllPublicConstructors() != null && other.getAllPublicConstructors()) {
            setAllPublicConstructors(true);
        }
        if (other.getAllPublicClasses() != null && other.getAllPublicClasses()) {
            setAllPublicClasses(true);
        }
        if (other.getQueryAllDeclaredMethods() != null && other.getQueryAllDeclaredMethods()) {
            setQueryAllDeclaredMethods(true);
        }
        if (other.getQueryAllDeclaredConstructors() != null && other.getQueryAllDeclaredConstructors()) {
            setQueryAllDeclaredConstructors(true);
        }
        if (other.getQueryAllPublicMethods() != null && other.getQueryAllPublicMethods()) {
            setQueryAllPublicMethods(true);
        }
        if (other.getQueryAllPublicConstructors() != null && other.getQueryAllPublicConstructors()) {
            setQueryAllPublicConstructors(true);
        }
        if (other.getUnsafeAllocated() != null && other.getUnsafeAllocated()) {
            setUnsafeAllocated(true);
        }
        setFields(merge(other.getFields(), getFields()));
        setMethods(merge(other.getMethods(), getMethods()));
        setQueriedMethods(merge(other.getQueriedMethods(), getQueriedMethods()));
    }

    private <T> Collection<T> merge(final Collection<T> v1, final Collection<T> v2) {
        if (v1 == null && v2 == null) {
            return null;
        }
        return Stream.of(v1, v2).filter(Objects::nonNull).flatMap(Collection::stream).distinct().collect(toList());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldReflectionModel {
        private String name;
        private Boolean allowWrite;
        private Boolean allowUnsafeAccess;

        public FieldReflectionModel(final String name, final Boolean allowWrite) {
            this.name = name;
            this.allowWrite = allowWrite;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodReflectionModel {
        private String name;
        private Collection<Class<?>> parameterTypes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Condition {
        private String typeReachable;
    }
}

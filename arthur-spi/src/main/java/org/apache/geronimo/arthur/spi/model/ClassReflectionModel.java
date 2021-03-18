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

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassReflectionModel {
    private String name;
    private Boolean allDeclaredConstructors;
    private Boolean allPublicConstructors;
    private Boolean allDeclaredMethods;
    private Boolean allPublicMethods;
    private Boolean allDeclaredClasses;
    private Boolean allPublicClasses;
    private Boolean allDeclaredFields;
    private Boolean allPublicFields;
    private Collection<FieldReflectionModel> fields;
    private Collection<MethodReflectionModel> methods;

    public ClassReflectionModel allPublic(final String name) {
        return new ClassReflectionModel(name, null, true, null, true, null, null, null, true, null, null);
    }

    public ClassReflectionModel allPublicConstructors(final String name) {
        return new ClassReflectionModel(name, null, true, null, null, null, null, null, null, null, null);
    }

    public ClassReflectionModel allDeclaredConstructors(final String name) {
        return new ClassReflectionModel(name, null, null, true, null, null, null, null, null, null, null);
    }

    public ClassReflectionModel allDeclared(final String name) {
        return new ClassReflectionModel(name, true, null, true, null, null, null, true, null, null, null);
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldReflectionModel {
        private String name;
        private Boolean allowWrite;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodReflectionModel {
        private String name;
        private Collection<Class<?>> parameterTypes;
    }
}

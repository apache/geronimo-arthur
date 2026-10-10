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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Describes a class to register for runtime reflection (and, when needed, JNI access) into the
 * native image.
 */
public class ClassReflectionModel {
    /**
     * Modern GraalVM reflection descriptor: the fully qualified class name, serialized under the
     * {@code "type"} key. Prefer over the legacy {@link #getName() name} when building for a
     * modern (combined-metadata) GraalVM.
     */
    private String type;
    /**
     * Legacy GraalVM reflection descriptor key ({@code "name"}). Not GraalVM 25 friendly: the
     * combined {@code reachability-metadata.json} uses the {@code "type"} descriptor. Retained for
     * backward compatibility; arthur maps a non-null {@code name} onto {@code type} when building
     * for a modern GraalVM.
     *
     * @deprecated use {@link #getType() type} ({@link #forType(String)}) instead (GraalVM 25/combined-metadata friendly).
     */
    @Deprecated
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
    private Boolean serializable;
    private Collection<FieldReflectionModel> fields;
    private Collection<MethodReflectionModel> methods;
    private Collection<MethodReflectionModel> queriedMethods;

    public ClassReflectionModel() {
    }

    /**
     * @param name the legacy (regex) reflection class name.
     * @deprecated use {@link #forType(String)} instead.
     */
    @Deprecated
    public ClassReflectionModel(final String name) {
        this.name = name;
    }

    public ClassReflectionModel(final String name, final String type) {
        this.name = name;
        this.type = type;
    }

    public ClassReflectionModel(final String name,
                                final Boolean allDeclaredConstructors, final Boolean allPublicConstructors,
                                final Boolean allDeclaredMethods, final Boolean allPublicMethods,
                                final Boolean allDeclaredClasses, final Boolean allPublicClasses,
                                final Boolean allDeclaredFields, final Boolean allPublicFields,
                                final Collection<FieldReflectionModel> fields, final Collection<MethodReflectionModel> methods) {
        this.name = name;
        this.allDeclaredConstructors = allDeclaredConstructors;
        this.allPublicConstructors = allPublicConstructors;
        this.allDeclaredMethods = allDeclaredMethods;
        this.allPublicMethods = allPublicMethods;
        this.allDeclaredClasses = allDeclaredClasses;
        this.allPublicClasses = allPublicClasses;
        this.allDeclaredFields = allDeclaredFields;
        this.allPublicFields = allPublicFields;
        this.fields = fields;
        this.methods = methods;
    }

    /**
     * @deprecated use {@link ClassReflectionModel#forType(String)} then the {@code all*} setters.
     */
    @Deprecated
    public ClassReflectionModel allPublic(final String name) {
        final ClassReflectionModel model = new ClassReflectionModel(name);
        model.setAllPublicConstructors(true);
        model.setAllPublicMethods(true);
        model.setAllPublicFields(true);
        return model;
    }

    /**
     * @deprecated use {@link ClassReflectionModel#forType(String)} then the {@code all*} setters.
     */
    @Deprecated
    public ClassReflectionModel allPublicConstructors(final String name) {
        final ClassReflectionModel model = new ClassReflectionModel(name);
        model.setAllPublicConstructors(true);
        return model;
    }

    /**
     * @deprecated use {@link ClassReflectionModel#forType(String)} then the {@code all*} setters.
     */
    @Deprecated
    public ClassReflectionModel allDeclaredConstructors(final String name) {
        final ClassReflectionModel model = new ClassReflectionModel(name);
        model.setAllDeclaredConstructors(true);
        return model;
    }

    /**
     * @deprecated use {@link ClassReflectionModel#forType(String)} then the {@code all*} setters.
     */
    @Deprecated
    public ClassReflectionModel allDeclared(final String name) {
        final ClassReflectionModel model = new ClassReflectionModel(name);
        model.setAllDeclaredConstructors(true);
        model.setAllDeclaredMethods(true);
        model.setAllDeclaredFields(true);
        return model;
    }

    /**
     * Creates a modern (GlVM 25 combined-metadata) reflection descriptor.
     * @param type the fully qualified class name (serialized under the {@code "type"} key).
     * @return the model to configure.
     */
    public static ClassReflectionModel forType(final String type) {
        final ClassReflectionModel model = new ClassReflectionModel();
        model.setType(type);
        return model;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    /**
     * @deprecated use {@link #getType() type}.
     */
    @Deprecated
    public String getName() {
        return name;
    }

    /**
     * @deprecated use {@link #setType(String)}.
     */
    @Deprecated
    public void setName(final String name) {
        this.name = name;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    public Boolean getAllDeclaredConstructors() {
        return allDeclaredConstructors;
    }

    public void setAllDeclaredConstructors(final Boolean allDeclaredConstructors) {
        this.allDeclaredConstructors = allDeclaredConstructors;
    }

    public Boolean getAllPublicConstructors() {
        return allPublicConstructors;
    }

    public void setAllPublicConstructors(final Boolean allPublicConstructors) {
        this.allPublicConstructors = allPublicConstructors;
    }

    public Boolean getAllDeclaredMethods() {
        return allDeclaredMethods;
    }

    public void setAllDeclaredMethods(final Boolean allDeclaredMethods) {
        this.allDeclaredMethods = allDeclaredMethods;
    }

    public Boolean getAllPublicMethods() {
        return allPublicMethods;
    }

    public void setAllPublicMethods(final Boolean allPublicMethods) {
        this.allPublicMethods = allPublicMethods;
    }

    public Boolean getAllDeclaredClasses() {
        return allDeclaredClasses;
    }

    public void setAllDeclaredClasses(final Boolean allDeclaredClasses) {
        this.allDeclaredClasses = allDeclaredClasses;
    }

    public Boolean getAllPublicClasses() {
        return allPublicClasses;
    }

    public void setAllPublicClasses(final Boolean allPublicClasses) {
        this.allPublicClasses = allPublicClasses;
    }

    public Boolean getAllDeclaredFields() {
        return allDeclaredFields;
    }

    public void setAllDeclaredFields(final Boolean allDeclaredFields) {
        this.allDeclaredFields = allDeclaredFields;
    }

    public Boolean getAllPublicFields() {
        return allPublicFields;
    }

    public void setAllPublicFields(final Boolean allPublicFields) {
        this.allPublicFields = allPublicFields;
    }

    public Boolean getQueryAllDeclaredMethods() {
        return queryAllDeclaredMethods;
    }

    public void setQueryAllDeclaredMethods(final Boolean queryAllDeclaredMethods) {
        this.queryAllDeclaredMethods = queryAllDeclaredMethods;
    }

    public Boolean getQueryAllDeclaredConstructors() {
        return queryAllDeclaredConstructors;
    }

    public void setQueryAllDeclaredConstructors(final Boolean queryAllDeclaredConstructors) {
        this.queryAllDeclaredConstructors = queryAllDeclaredConstructors;
    }

    public Boolean getQueryAllPublicMethods() {
        return queryAllPublicMethods;
    }

    public void setQueryAllPublicMethods(final Boolean queryAllPublicMethods) {
        this.queryAllPublicMethods = queryAllPublicMethods;
    }

    public Boolean getQueryAllPublicConstructors() {
        return queryAllPublicConstructors;
    }

    public void setQueryAllPublicConstructors(final Boolean queryAllPublicConstructors) {
        this.queryAllPublicConstructors = queryAllPublicConstructors;
    }

    public Boolean getUnsafeAllocated() {
        return unsafeAllocated;
    }

    public void setUnsafeAllocated(final Boolean unsafeAllocated) {
        this.unsafeAllocated = unsafeAllocated;
    }

    public Boolean getSerializable() {
        return serializable;
    }

    public void setSerializable(final Boolean serializable) {
        this.serializable = serializable;
    }

    public Collection<FieldReflectionModel> getFields() {
        return fields;
    }

    public void setFields(final Collection<FieldReflectionModel> fields) {
        this.fields = fields;
    }

    public Collection<MethodReflectionModel> getMethods() {
        return methods;
    }

    public void setMethods(final Collection<MethodReflectionModel> methods) {
        this.methods = methods;
    }

    public Collection<MethodReflectionModel> getQueriedMethods() {
        return queriedMethods;
    }

    public void setQueriedMethods(final Collection<MethodReflectionModel> queriedMethods) {
        this.queriedMethods = queriedMethods;
    }

    /**
     * The resolved class name (modern {@code type} or legacy {@code name}).
     * @return the class name used for reflection.
     */
    public String className() {
        return type != null ? type : name;
    }

    /**
     * Renders this descriptor in the modern combined {@code reachability-metadata.json}
     * {@code reflection} entry shape (uses {@link #className()} under the {@code "type"} key).
     * @return a JSON-B serializable map.
     */
    public Map<String, Object> toModernEntry() {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", className());
        putTrue(entry, "allDeclaredConstructors", allDeclaredConstructors);
        putTrue(entry, "allPublicConstructors", allPublicConstructors);
        putTrue(entry, "allDeclaredMethods", allDeclaredMethods);
        putTrue(entry, "allPublicMethods", allPublicMethods);
        putTrue(entry, "allDeclaredClasses", allDeclaredClasses);
        putTrue(entry, "allPublicClasses", allPublicClasses);
        putTrue(entry, "allDeclaredFields", allDeclaredFields);
        putTrue(entry, "allPublicFields", allPublicFields);
        putTrue(entry, "unsafeAllocated", unsafeAllocated);
        putTrue(entry, "serializable", serializable);
        if (fields != null && !fields.isEmpty()) {
            entry.put("fields", fields.stream().map(FieldReflectionModel::toModernEntry).collect(Collectors.toList()));
        }
        if (methods != null && !methods.isEmpty()) {
            entry.put("methods", methods.stream().map(MethodReflectionModel::toModernEntry).collect(Collectors.toList()));
        }
        return entry;
    }

    /**
     * Renders a {@code jni} entry for this descriptor iff it carries at least one
     * {@link FieldReflectionModel#getJniAccessible() jniAccessible} field; otherwise returns
     * {@code null} (no JNI section needed).
     * @return a JSON-B serializable map or {@code null}.
     */
    public Map<String, Object> toJniEntry() {
        if (fields == null) {
            return null;
        }
        final boolean anyJni = fields.stream().anyMatch(f -> Boolean.TRUE.equals(f.getJniAccessible()));
        if (!anyJni) {
            return null;
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", className());
        entry.put("fields", fields.stream().filter(f -> Boolean.TRUE.equals(f.getJniAccessible()))
                .map(FieldReflectionModel::toJniEntry).collect(Collectors.toList()));
        return entry;
    }

    private void putTrue(final Map<String, Object> map, final String key, final Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            map.put(key, true);
        }
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
        if (other.getSerializable() != null && other.getSerializable()) {
            setSerializable(true);
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

    /**
     * Describes a field to register for reflection (and optionally JNI access).
     */
    public static class FieldReflectionModel {
        private String name;
        private Boolean allowWrite;
        private Boolean allowUnsafeAccess;

        /**
         * Modern GraalVM field descriptor: whether the field should also be accessible through JNI
         * (which surfaces the field in the {@code "jni"} section of the combined metadata).
         */
        private Boolean jniAccessible;

        public FieldReflectionModel() {
        }

        public FieldReflectionModel(final String name, final Boolean allowWrite, final Boolean allowUnsafeAccess, final Boolean jniAccessible) {
            this.name = name;
            this.allowWrite = allowWrite;
            this.allowUnsafeAccess = allowUnsafeAccess;
            this.jniAccessible = jniAccessible;
        }

        public FieldReflectionModel(final String name, final Boolean allowWrite) {
            this.name = name;
            this.allowWrite = allowWrite;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Boolean getAllowWrite() {
            return allowWrite;
        }

        public void setAllowWrite(final Boolean allowWrite) {
            this.allowWrite = allowWrite;
        }

        public Boolean getAllowUnsafeAccess() {
            return allowUnsafeAccess;
        }

        public void setAllowUnsafeAccess(final Boolean allowUnsafeAccess) {
            this.allowUnsafeAccess = allowUnsafeAccess;
        }

        public Boolean getJniAccessible() {
            return jniAccessible;
        }

        public void setJniAccessible(final Boolean jniAccessible) {
            this.jniAccessible = jniAccessible;
        }

        public Map<String, Object> toModernEntry() {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            if (Boolean.TRUE.equals(allowWrite)) {
                entry.put("allowWrite", true);
            }
            if (Boolean.TRUE.equals(allowUnsafeAccess)) {
                entry.put("allowUnsafeAccess", true);
            }
            if (Boolean.TRUE.equals(jniAccessible)) {
                entry.put("jniAccessible", true);
            }
            return entry;
        }

        public Map<String, Object> toJniEntry() {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            if (Boolean.TRUE.equals(allowUnsafeAccess)) {
                entry.put("allowUnsafeAccess", true);
            }
            return entry;
        }
    }

    /**
     * Describes a method to register for reflection.
     */
    public static class MethodReflectionModel {
        private String name;
        private Collection<Class<?>> parameterTypes;

        public MethodReflectionModel() {
        }

        public MethodReflectionModel(final String name, final Collection<Class<?>> parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Collection<Class<?>> getParameterTypes() {
            return parameterTypes;
        }

        public void setParameterTypes(final Collection<Class<?>> parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        public Map<String, Object> toModernEntry() {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            if (parameterTypes != null && !parameterTypes.isEmpty()) {
                entry.put("parameterTypes", parameterTypes.stream().map(Class::getName).collect(Collectors.toList()));
            }
            return entry;
        }
    }

    /**
     * A condition (e.g. {@code typeReachable}) attached to a reflection descriptor.
     */
    public static class Condition {
        private String typeReachable;

        public Condition() {
        }

        public Condition(final String typeReachable) {
            this.typeReachable = typeReachable;
        }

        public String getTypeReachable() {
            return typeReachable;
        }

        public void setTypeReachable(final String typeReachable) {
            this.typeReachable = typeReachable;
        }
    }
}
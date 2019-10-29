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

import lombok.Data;

@Data
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

    @Data
    public static class FieldReflectionModel {
        private String name;
        private Boolean allowWrite;
    }

    @Data
    public static class MethodReflectionModel {
        private String name;
        private Collection<Class<?>> parameterTypes;
    }
}

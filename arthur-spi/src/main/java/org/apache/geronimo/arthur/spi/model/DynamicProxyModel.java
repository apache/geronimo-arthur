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

/**
 * Describes a dynamic proxy (a set of interfaces) to register into the native image.
 * <p>
 * Dynamic proxies are kept in their own {@code proxy-config.json} (GraalVM keeps them out of the
 * combined {@code reachability-metadata.json}).
 */
public class DynamicProxyModel {
    private Collection<String> classes;

    public DynamicProxyModel() {
    }

    public DynamicProxyModel(final Collection<String> classes) {
        this.classes = classes;
    }

    public Collection<String> getClasses() {
        return classes;
    }

    public void setClasses(final Collection<String> classes) {
        this.classes = classes;
    }
}
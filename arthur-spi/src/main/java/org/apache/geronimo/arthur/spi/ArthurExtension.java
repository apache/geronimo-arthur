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
package org.apache.geronimo.arthur.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;

import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

/**
 * Enable to enrich the build with some metadata.
 */
public interface ArthurExtension {
    /**
     * @return the priority of this extension (natural order sorting).
     */
    default int order() {
        return 0;
    }

    /**
     * @param context current build context.
     */
    void execute(Context context);

    interface Context {
        <T extends Annotation> Collection<Class<?>> findAnnotatedClasses(Class<T> annotation);

        <T extends Annotation> Collection<Method> findAnnotatedMethods(Class<T> annotation);

        <T> Collection<Class<? extends T>> findImplementations(Class<T> parent);

        void register(ClassReflectionModel classReflectionModel);

        void register(ResourceModel resourceModel);

        void register(ResourceBundleModel resourceModel);

        void register(DynamicProxyModel dynamicProxyModel);

        void enableAllSecurityServices();

        void enableAllCharsets();

        void initializeAtBuildTime(String... classes);

        <T> T unwrap(Class<T> type);
    }
}

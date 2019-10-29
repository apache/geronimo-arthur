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
package org.apache.geronimo.arthur.maven.extension;

import java.util.Collection;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import lombok.RequiredArgsConstructor;

public class MavenArthurExtension implements ArthurExtension {
    private static final ThreadLocal<Ctx> CONTEXT = new ThreadLocal<>();

    @Override
    public int order() {
        return 99;
    }

    @Override
    public void execute(final Context context) {
        final Ctx ctx = CONTEXT.get();
        if (ctx == null) {
            return;
        }
        if (ctx.reflections != null && !ctx.reflections.isEmpty()) {
            ctx.reflections.forEach(context::register);
        }
        if (ctx.resources != null && !ctx.resources.isEmpty()) {
            ctx.resources.forEach(context::register);
        }
        if (ctx.bundles != null && !ctx.bundles.isEmpty()) {
            ctx.bundles.forEach(context::register);
        }
        if (ctx.dynamicProxies != null && !ctx.dynamicProxies.isEmpty()) {
            ctx.dynamicProxies.forEach(context::register);
        }
    }

    public static void with(final Collection<ClassReflectionModel> reflections,
                            final Collection<ResourceModel> resources,
                            final Collection<ResourceBundleModel> bundles,
                            final Collection<DynamicProxyModel> dynamicProxies,
                            final Runnable task) {
        CONTEXT.set(new Ctx(reflections, resources, bundles, dynamicProxies));
        try {
            task.run();
        } finally {
            CONTEXT.remove();
        }
    }

    @RequiredArgsConstructor
    private static class Ctx {
        private final Collection<ClassReflectionModel> reflections;
        private final Collection<ResourceModel> resources;
        private final Collection<ResourceBundleModel> bundles;
        private final Collection<DynamicProxyModel> dynamicProxies;
    }
}

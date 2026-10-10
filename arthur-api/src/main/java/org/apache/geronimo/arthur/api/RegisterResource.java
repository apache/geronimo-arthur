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
package org.apache.geronimo.arthur.api;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
public @interface RegisterResource {
    /**
     * @deprecated legacy regex resource patterns. Not GraalVM 25 friendly: use {@link #globs()}
     * (glob-based combined-metadata resource descriptors) instead.
     */
    @Deprecated
    String[] patterns() default {};

    /**
     * Glob resource descriptors (GraalVM 21+/combined metadata), e.g. {@code "config/*.properties"}.
     * Each glob is registered as-is on modern GraalVM; on legacy GraalVM they are ignored.
     */
    String[] globs() default {};

    String[] bundles() default {};
}

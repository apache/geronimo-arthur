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
package org.apache.geronimo.arthur.documentation.lang;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PathPredicates {
    public Predicate<Path> createFilter(final List<String> includes, final List<String> excludes) {
        return createFilter(includes).and(createFilter(excludes).negate());
    }

    public Predicate<Path> createFilter(final List<String> patterns) {
        return patterns == null || patterns.isEmpty() ?
                p -> true :
                patterns.stream()
                        .map(Pattern::compile)
                        .map(Pattern::asPredicate)
                        .map(pattern -> (Predicate<Path>) path -> pattern.test(path.toString()))
                        .reduce(p -> false, Predicate::or);
    }
}

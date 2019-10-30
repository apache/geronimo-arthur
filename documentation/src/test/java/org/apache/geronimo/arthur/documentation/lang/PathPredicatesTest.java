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

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class PathPredicatesTest {
    @Test
    void nullValue() {
        final Predicate<Path> adoc = new PathPredicates().createFilter(null);
        assertTrue(adoc.test(Paths.get("foo.adoc")));
        assertTrue(adoc.test(Paths.get(".foo.adoc")));
        assertTrue(adoc.test(Paths.get(".git")));
        assertTrue(adoc.test(Paths.get("whatever")));
    }

    @Test
    void simple() {
        final Predicate<Path> adoc = new PathPredicates().createFilter(singletonList(".+\\.adoc"));
        assertTrue(adoc.test(Paths.get("foo.adoc")));
        assertTrue(adoc.test(Paths.get(".foo.adoc")));
        assertFalse(adoc.test(Paths.get(".git")));
        assertFalse(adoc.test(Paths.get("whatever")));

        final Predicate<Path> dotted = new PathPredicates().createFilter(singletonList("^\\..+"));
        assertFalse(dotted.test(Paths.get("foo.adoc")));
        assertTrue(dotted.test(Paths.get(".foo.adoc")));
        assertTrue(dotted.test(Paths.get(".git")));
        assertFalse(dotted.test(Paths.get("whatever")));
    }

    @Test
    void includeExclude() {
        final Predicate<Path> filter = new PathPredicates().createFilter(singletonList(".+\\.adoc"), singletonList("^\\..+"));
        assertTrue(filter.test(Paths.get("foo.adoc")));
        assertFalse(filter.test(Paths.get(".foo.adoc")));
        assertFalse(filter.test(Paths.get(".git")));
        assertFalse(filter.test(Paths.get("whatever")));
    }

    @Test
    void includeExcludeNullInclude() {
        final Predicate<Path> filter = new PathPredicates().createFilter(null, singletonList("^\\..+"));
        assertTrue(filter.test(Paths.get("foo.adoc")));
        assertFalse(filter.test(Paths.get(".foo.adoc")));
        assertFalse(filter.test(Paths.get(".git")));
        assertTrue(filter.test(Paths.get("whatever")));
    }
}

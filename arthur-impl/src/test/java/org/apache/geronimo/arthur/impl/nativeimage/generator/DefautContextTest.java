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
package org.apache.geronimo.arthur.impl.nativeimage.generator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefautContextTest {
    @ParameterizedTest
    @CsvSource({
            "null,whatever,EQUALS,true",
            "null,whatever,STARTS_WITH,true",
            "null,whatever,MATCHES,true",
            "foo,foo,EQUALS,true",
            "foo,foo,STARTS_WITH,true",
            "foo,foo,MATCHES,true",
            "foo,foo-,STARTS_WITH,true",
            "foo.*,foo,MATCHES,true",
            "foo,foo-,EQUALS,false",
            "foo,bar,STARTS_WITH,false"
    })
    void predicate(final String prop, final String value, final ArthurExtension.PredicateType type, final boolean result) {
        final Optional<Predicate<String>> predicate = new DefautContext(
                new ArthurNativeImageConfiguration(), null, null, null,
                "null".equals(prop) ? emptyMap() : singletonMap("includes", prop))
                .createPredicate("includes", type);
        assertEquals(result, !predicate.isPresent() || predicate.orElseThrow(IllegalStateException::new).test(value));
    }

    @ParameterizedTest
    @CsvSource({
            "null,null,whatever,EQUALS,true",
            "null,null,whatever,STARTS_WITH,true",
            "null,null,whatever,MATCHES,true",
            "foo,null,foo,EQUALS,true",
            "foo,null,foo,STARTS_WITH,true",
            "foo,null,foo,MATCHES,true",
            "foo,null,foo-,STARTS_WITH,true",
            "foo.*,null,foo,MATCHES,true",
            "foo,null,foo-,EQUALS,false",
            "foo,null,bar,STARTS_WITH,false",
            "foo,foo,bar,STARTS_WITH,false",
            "bar,whatever,bar,STARTS_WITH,true",
            "null,bar,bar,STARTS_WITH,false"
    })
    void includeExclude(final String includesProp, final String excludesProp, final String value,
                        final ArthurExtension.PredicateType type, final boolean result) {
        final Map<String, String> map = new HashMap<>();
        if (!"null".equals(includesProp)) {
            map.put("includes", includesProp);
        }
        if (!"null".equals(excludesProp)) {
            map.put("excludes", excludesProp);
        }
        final DefautContext context = new DefautContext(new ArthurNativeImageConfiguration(), null, null, null, map);
        assertEquals(result, context.createIncludesExcludes("", type).test(value));
    }

    @Test
    void findHierarchy() {
        final ArthurExtension.Context context = new DefautContext(new ArthurNativeImageConfiguration(), null, null, null, emptyMap());
        assertEquals(singletonList(StandaloneClass.class), context.findHierarchy(StandaloneClass.class).collect(toList()));
        assertEquals(asList(ChildClass.class, StandaloneClass.class), context.findHierarchy(ChildClass.class).collect(toList()));
        assertEquals(asList(ImplClass.class, StandaloneInterface.class), context.findHierarchy(ImplClass.class).collect(toList()));
        assertEquals(asList(ChildAndImplClass.class, StandaloneClass.class, StandaloneInterface.class), context.findHierarchy(ChildAndImplClass.class).collect(toList()));
    }

    public static class StandaloneClass {
    }

    public interface StandaloneInterface {
    }

    public static class ChildClass extends StandaloneClass {
    }

    public static class ChildAndImplClass extends StandaloneClass implements StandaloneInterface {
    }

    public static class ImplClass implements StandaloneInterface {
    }
}

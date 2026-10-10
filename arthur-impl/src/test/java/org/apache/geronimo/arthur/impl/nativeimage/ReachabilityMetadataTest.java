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
package org.apache.geronimo.arthur.impl.nativeimage;

import org.apache.geronimo.arthur.impl.nativeimage.generator.ConfigurationGenerator;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;
import org.junit.jupiter.api.Test;

import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachabilityMetadataTest {

    private static ConfigurationGenerator generator(final ArthurNativeImageConfiguration configuration, final String workdir) {
        return new ConfigurationGenerator(
                singletonList(context -> {
                    // legacy regex pattern - must be migrated to a glob on the modern path
                    final ResourceModel legacy = new ResourceModel();
                    legacy.setPattern("dev/tamboui/tui/bindings/*\\.properties");
                    context.register(legacy);
                    // modern glob descriptor
                    context.register(ResourceModel.glob("config/*.properties", "my.module"));
                    // bundle
                    context.register(new ResourceBundleModel("com.example.Messages"));
                }),
                configuration, Paths.get("target/tests/ReachabilityMetadataTest/" + workdir),
                toJson(), k -> Collections.emptyList(), k -> Collections.emptyList(), k -> Collections.emptyList(),
                (Function<Class<?>, Collection<Class<?>>>) k -> Collections.emptyList(), Collections.emptyMap());
    }

    private static java.util.function.BiConsumer<Object, Writer> toJson() {
        // minimal JSON-B based serializer matching the mojo (Johnzon is on the test classpath)
        final javax.json.bind.Jsonb jsonb = javax.json.bind.JsonbBuilder.create();
        return (o, writer) -> {
            try {
                writer.write(jsonb.toJson(o));
            } catch (final java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void modernMetadataEmitsCombinedReachabilityMetadata() throws Exception {
        final ArthurNativeImageConfiguration configuration = new ArthurNativeImageConfiguration();
        configuration.complete("25.0.2", false); // modern GraalVM
        generator(configuration, "modern").run();

        final Path metadata = Paths.get("target/tests/ReachabilityMetadataTest/modern/META-INF/native-image/org.apache.geronimo.arthur/reachability-metadata.json");
        assertTrue(Files.exists(metadata), "modern path must emit reachability-metadata.json");
        final String content = new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8);

        // the legacy regex pattern must have been migrated to a glob (no raw 'pattern' key, no invalid '.*' regex)
        assertTrue(content.contains("\"glob\":\"dev/tamboui/tui/bindings/*.properties\""), "pattern must migrate to glob: " + content);
        assertTrue(content.contains("\"glob\":\"config/*.properties\""), "explicit glob must be kept: " + content);
        assertTrue(content.contains("\"module\":\"my.module\""), "module must be preserved: " + content);
        assertTrue(content.contains("\"bundle\":\"com.example.Messages\""), "bundle must be emitted: " + content);
        assertFalse(content.contains("\"pattern\""), "modern output must not contain a raw legacy pattern key");
    }

    @Test
    void legacyMetadataKeepsLegacyEmissionBelowThreshold() throws Exception {
        final ArthurNativeImageConfiguration configuration = new ArthurNativeImageConfiguration();
        configuration.complete("21.3.0", false); // legacy GraalVM
        generator(configuration, "legacy").run();

        assertFalse(Files.exists(Paths.get("target/tests/ReachabilityMetadataTest/legacy/META-INF/native-image/org.apache.geronimo.arthur/reachability-metadata.json")),
                "legacy path must not emit reachability-metadata.json");
        final Path legacy = Paths.get("target/tests/ReachabilityMetadataTest/legacy/resources.arthur.json");
        assertTrue(Files.exists(legacy), "legacy path must emit resources.arthur.json");
        final String content = new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8);
        assertTrue(content.contains("dev/tamboui/tui/bindings/"), "legacy path must keep the raw pattern: " + content);
        assertTrue(content.contains("\\\\.properties") || content.contains("*.properties"), "legacy path must keep the pattern value: " + content);
    }

    @Test
    void flagNames() {
        assertEquals(25, ArthurNativeImageConfiguration.REACHABILITY_METADATA_MIN_MAJOR);
    }
}
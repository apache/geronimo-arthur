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
package org.apache.geronimo.arthur.knight.winegrower.feature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
public class WinegrowerFeature implements Feature {
    public static final class Options {
        @Option(help = "Winegrower index properties.", type = OptionType.User)
        static final HostedOptionKey<String> WinegrowerIndex = new HostedOptionKey<>(null);

        @Option(help = "Winegrower manifests properties.", type = OptionType.User)
        static final HostedOptionKey<String> WinegrowerManifests = new HostedOptionKey<>(null);
    }

    // org.graalvm.compiler.options.processor is not on central
    public static class WinegrowerOptions implements OptionDescriptors {
        @Override
        public OptionDescriptor get(final String value) {
            switch (value) {
                case "WinegrowerIndex":
                    return OptionDescriptor.create(
                            value, OptionType.User, String.class,
                            "Winegrower index properties.",
                            Options.class, value,
                            Options.WinegrowerIndex);
                case "WinegrowerManifests":
                    return OptionDescriptor.create(
                            value, OptionType.User, String.class,
                            "Winegrower manifests properties.",
                            Options.class, value,
                            Options.WinegrowerManifests);
                default:
                    return null;
            }
        }

        @Override
        public Iterator<OptionDescriptor> iterator() {
            return Stream.of("WinegrowerIndex", "WinegrowerManifests").map(this::get).iterator();
        }
    }

    @Override
    public void beforeAnalysis(final BeforeAnalysisAccess access) {
        if (Options.WinegrowerIndex.hasBeenSet()) {
            register(Options.WinegrowerIndex.getValue(), "WINEGROWER-INF/index.properties");
        }
        if (Options.WinegrowerManifests.hasBeenSet()) {
            register(Options.WinegrowerManifests.getValue(), "WINEGROWER-INF/manifests.properties");
        }
    }

    private void register(final String path, final String resource) {
        try (final InputStream stream = Files.newInputStream(Paths.get(path))) {
            Resources.registerResource(resource, stream);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

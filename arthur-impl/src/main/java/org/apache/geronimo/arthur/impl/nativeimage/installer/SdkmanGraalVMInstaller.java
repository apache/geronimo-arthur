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
package org.apache.geronimo.arthur.impl.nativeimage.installer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.geronimo.arthur.impl.nativeimage.process.ProcessExecutor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

@Slf4j
@RequiredArgsConstructor
public class SdkmanGraalVMInstaller {
    private final SdkmanGraalVMInstallerConfiguration configuration;

    private Path home;

    public Path install() {
        Path archive;
        try {
            archive = configuration.getResolver().apply(configuration.getGav());
        } catch (final IllegalStateException ise) {
            if (configuration.isOffline()) {
                throw new IllegalStateException("GraalVM was not found and mvn runs offline");
            }
            try {
                if (!Files.exists(configuration.getWorkdir())) {
                    Files.createDirectories(configuration.getWorkdir());
                }

                log.info("Downloading GraalVM {}, this can be long", configuration.getVersion());
                final Path download = download();

                log.info("Installing GraalVM {}", configuration.getGav());
                archive = configuration.getInstaller().apply(configuration.getGav(), download);
            } catch (final IOException e) {
                throw new IllegalStateException("Can't cache graal locally", e);
            }
        }
        if (!Files.exists(archive)) {
            throw new IllegalStateException("No graal archive available: " + archive);
        }

        final Path exploded = archive.getParent().resolve("distribution_exploded");
        if (!Files.isDirectory(exploded)) {
            configuration.getExtractor().accept(archive, exploded);
        }
        home = exploded;
        // if macos
        if (Files.isDirectory(home.resolve("Contents/Home"))) {
            home = home.resolve("Contents/Home");
        }
        return home;
    }

    public Path installNativeImage() {
        final Path bin = requireNonNull(this.home, "No home, ensure to call install() before installNativeImage()")
                .resolve("bin");
        try {
            if (!findNativeImage(bin).isPresent()) { // likely only UNIx, windows comes with native-image.cmd
                log.info("Installing native-image");
                new ProcessExecutor(
                        configuration.isInheritIO(), asList(findGu(bin).toAbsolutePath().toString(), "install", "native-image"),
                        singletonMap("GRAALVM_HOME", home.toString())).run();
            } else {
                log.debug("native-image is already available");
            }
            return findNativeImage(bin)
                    .orElseThrow(() -> new IllegalArgumentException("No native-image found in " + bin));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<Path> findNativeImage(final Path bin) throws IOException {
        try (final Stream<Path> list = Files.list(bin)) {
            return list.filter(path -> {
                final String name = path.getFileName().toString();
                return name.equals("native-image") || name.startsWith("native-image.") /*win*/;
            }).min(comparing(p -> p.getFileName().toString().length())); // support windows this way (.cmd);
        }
    }

    private Path findGu(final Path bin) {
        try (final Stream<Path> list = Files.list(bin)) {
            return list
                    .filter(path -> path.getFileName().toString().startsWith("gu"))
                    .min(comparing(p -> p.getFileName().toString().length()))
                    .orElseThrow(() -> new IllegalStateException("No gu found in " + bin));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path download() throws IOException {
        final String fname = "graal-" + configuration.getVersion() + "-" + configuration.getPlatform() + "." + configuration.getGav().split(":")[2];
        final Path cache = configuration.getWorkdir().resolve(fname);
        if (Files.exists(cache)) {
            return cache;
        }

        final URL source = new URL(configuration.getUrl());
        final HttpURLConnection connection = HttpURLConnection.class.cast(source.openConnection());
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true); // sdkman broker redirects on github
        try (final InputStream stream = new BufferedInputStream(connection.getInputStream())) {
            Files.copy(stream, cache);
        } catch (final IOException ioe) {
            if (Files.exists(cache)) {
                Files.delete(cache);
            }
            throw ioe;
        }
        return cache;
    }
}

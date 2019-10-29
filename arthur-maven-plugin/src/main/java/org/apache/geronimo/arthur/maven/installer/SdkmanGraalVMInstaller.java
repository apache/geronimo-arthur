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
package org.apache.geronimo.arthur.maven.installer;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.geronimo.arthur.impl.nativeimage.process.ProcessExecutor;
import org.apache.maven.plugin.logging.Log;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SdkmanGraalVMInstaller {
    private final boolean offline;
    private final boolean inheritIO;
    private final String url;
    private final String version;
    private final String platform;
    private final String gav;
    private final Path workdir;
    private final Log log;
    private final Function<String, Path> resolver;
    private final BiFunction<String, Path, Path> installer;

    private Path home;

    public Path install() {
        Path archive;
        try {
            archive = resolver.apply(gav);
        } catch (final IllegalStateException ise) {
            if (offline) {
                throw new IllegalStateException("GraalVM was not found and mvn runs offline");
            }
            try {
                ensureExists(workdir);

                log.info("Downloading GraalVM " + version + ", this can be long");
                final Path download = download();

                log.info("Installing GraalVM " + gav);
                archive = installer.apply(gav, download);
            } catch (final IOException e) {
                throw new IllegalStateException("Can't cache graal locally", e);
            }
        }
        if (!Files.exists(archive)) {
            throw new IllegalStateException("No graal archive available: " + archive);
        }

        final Path exploded = archive.getParent().resolve("distribution_exploded");
        if (!Files.isDirectory(exploded)) {
            try {
                unpack(archive, exploded);
            } catch (final IOException e) {
                throw new IllegalStateException("Can't unpack graal archive", e);
            }
        }
        return home = exploded;
    }

    public Path installNativeImage() {
        final Path bin = requireNonNull(this.home, "No home, ensure to call install() before installNativeImage()")
                .resolve("bin");
        try {
            if (findNativeImage(bin).count() == 0) { // likely only UNIx, windows comes with native-image.cmd
                log.info("Installing native-image");
                new ProcessExecutor(inheritIO, asList(findGu(bin).toAbsolutePath().toString(), "install", "native-image")).run();
            } else {
                log.debug("native-image is already available");
            }
            return findNativeImage(bin)
                    .min(comparing(p -> p.getFileName().toString().length())) // support windows this way (.cmd)
                    .orElseThrow(() -> new IllegalArgumentException("No native-image found in " + bin));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Stream<Path> findNativeImage(final Path bin) throws IOException {
        return Files.list(bin).filter(path -> path.getFileName().toString().startsWith("native-image"));
    }

    private void unpack(final Path archive, final Path exploded) throws IOException {
        final boolean isZip = archive.getFileName().toString().endsWith(".zip");
        final InputStream fileStream = new BufferedInputStream(Files.newInputStream(archive));
        final Predicate<ArchiveEntry> isLink = isZip ?
                e -> ZipArchiveEntry.class.cast(e).isUnixSymlink() :
                e -> TarArchiveEntry.class.cast(e).isSymbolicLink();
        final BiFunction<ArchiveInputStream, ArchiveEntry, String> linkPath = isZip ?
                (a, e) -> { // todo: validate this with cygwin
                    try {
                        return new BufferedReader(new InputStreamReader(a)).readLine();
                    } catch (final IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                } :
                (a, e) -> TarArchiveEntry.class.cast(e).getLinkName();
        final Map<Path, Path> linksToCopy = new HashMap<>();
        final Map<Path, Path> linksToRetry = new HashMap<>();
        try (final ArchiveInputStream archiveInputStream = isZip ?
                new ZipArchiveInputStream(fileStream) :
                new TarArchiveInputStream(new GzipCompressorInputStream(fileStream))) {
            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                final String entryName = entry.getName();
                if (!archiveInputStream.canReadEntryData(entry)) {
                    log.error("Can't read '" + entryName + "'");
                    continue;
                }

                final int sep = entryName.indexOf('/');
                if (sep == entryName.length() || sep < 0) { // first level folder, skip
                    continue;
                }

                final Path target = exploded.resolve(entryName.substring(sep + 1));
                if (entry.isDirectory()) {
                    ensureExists(target);
                } else {
                    ensureExists(target.getParent());
                    if (isLink.test(entry)) {
                        final Path targetLinked = target.getParent().resolve(linkPath.apply(archiveInputStream, entry));
                        if (Files.exists(targetLinked)) {
                            try {
                                Files.createSymbolicLink(target, targetLinked);
                                setExecutableIfNeeded(target);
                            } catch (final IOException ioe) {
                                linksToCopy.put(target, targetLinked);
                            }
                        } else {
                            linksToRetry.put(target, targetLinked);
                        }
                    } else {
                        Files.copy(archiveInputStream, target, StandardCopyOption.REPLACE_EXISTING);
                        setExecutableIfNeeded(target);
                    }
                }
            }
        }
        linksToRetry.forEach((target, targetLinked) -> {
            try {
                Files.createSymbolicLink(target, targetLinked);
                setExecutableIfNeeded(target);
            } catch (final IOException ioe) {
                linksToCopy.put(target, targetLinked);
            }
        });
        linksToCopy.forEach((target, targetLinked) -> {
            if (!Files.exists(targetLinked)) {
                log.warn("No file '" + targetLinked + "' found, skipping link");
                return;
            }
            try {
                Files.copy(targetLinked, target, StandardCopyOption.REPLACE_EXISTING);
                setExecutableIfNeeded(target);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void setExecutableIfNeeded(final Path target) throws IOException {
        if (target.getParent().getFileName().toString().equals("bin") && !Files.isExecutable(target)) {
            Files.setPosixFilePermissions(
                    target,
                    Stream.of(
                            OWNER_READ, OWNER_EXECUTE, OWNER_WRITE,
                            GROUP_READ, GROUP_EXECUTE,
                            OTHERS_READ, OTHERS_EXECUTE)
                            .collect(toSet()));
        }
    }

    private void ensureExists(final Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
    }

    private Path findGu(final Path bin) {
        try {
            return Files.list(bin)
                    .filter(path -> path.getFileName().toString().startsWith("gu"))
                    .min(comparing(p -> p.getFileName().toString().length()))
                    .orElseThrow(() -> new IllegalStateException("No gu found in " + bin));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path download() throws IOException {
        final String fname = "graal-" + version + "-" + platform + "." + gav.split(":")[2];
        final Path cache = workdir.resolve(fname);
        if (Files.exists(cache)) {
            return cache;
        }

        final URL source = new URL(url);
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

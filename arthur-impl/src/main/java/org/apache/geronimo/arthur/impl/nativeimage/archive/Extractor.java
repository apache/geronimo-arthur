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
package org.apache.geronimo.arthur.impl.nativeimage.archive;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.stream.Collectors.toSet;

@Slf4j
public class Extractor {
    public void unpack(final Path archive, final Path exploded) {
        try {
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
                            Files.setLastModifiedTime(target, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));
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
        } catch (final IOException e) {
            throw new IllegalStateException("Can't unpack graal archive", e);
        }
    }

    private void setExecutableIfNeeded(final Path target) throws IOException {
        final String parentFilename = target.getParent().getFileName().toString();
        final String filename = target.getFileName().toString();
        if ((parentFilename.equals("bin") && !Files.isExecutable(target)) ||
                (parentFilename.equals("lib") && (
                        filename.contains("exec") || filename.startsWith("j") ||
                                (filename.startsWith("lib") && filename.contains(".so"))))) {
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
}

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

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.geronimo.arthur.impl.nativeimage.archive.Extractor;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstaller;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstallerConfiguration;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SdkmanGraalVMInstallerTest {
    @TempDir
    Path workdir;

    @ParameterizedTest
    @CsvSource({
            "19.2.1-grl,linux64,tar.gz", // tar.gz
            "19.2.1-grl,cygwin,zip" // zip
    })
    void install(final String version, final String platform) throws IOException {
        final String handledApi = "/broker/download/java/" + version + "/" + platform;
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/").setHandler(ex -> {
            final String path = ex.getRequestURI().getPath();
            if (path.endsWith(handledApi + "/redirected-as-sdkman-on-github")) {
                final byte[] bytes = createFakeArchive(version, platform).toByteArray();
                ex.getResponseHeaders().set("content-type", "application/octect-stream");
                ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            } else if (handledApi.equals(path)) {
                ex.getResponseHeaders().set("location", "http://localhost:" + server.getAddress().getPort() + path + "/redirected-as-sdkman-on-github");
                ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, 0);
                ex.close();
            } else {
                ex.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
                ex.close();
            }
        });
        try {
            server.start();

            final SpiedResolver resolver = new SpiedResolver();
            final SpiedInstaller artifactInstaller = new SpiedInstaller();
            final SdkmanGraalVMInstaller installer = newInstaller(
                    workdir, platform, version, "http://localhost:" + server.getAddress().getPort(),
                    artifactInstaller, resolver);
            final Path installed = installer.install();
            assertNotNull(installed);

            final Path gu = installed.resolve("bin/gu");
            assertTrue(Files.exists(gu));
            assertEquals("works", Files.lines(gu).collect(joining("\n")));

            assertEquals(1, resolver.counter.get());
            assertEquals(1, artifactInstaller.counter.get());

            // ensure we use the cache
            resolver.result = installed; // we should put the archive we we only check the parent anyway so not a big deal
            assertEquals(installed, installer.install());
            assertEquals(2, resolver.counter.get());
            assertEquals(1, artifactInstaller.counter.get());
        } finally {
            server.stop(0);
        }
    }

    private ByteArrayOutputStream createFakeArchive(final String version, final String platform) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final String rootName = "graal-ce-" + version + "/";
        final BiConsumer<ArchiveOutputStream, Function<String, ArchiveEntry>> prepareStructure = (archive, entryFactory) -> {
            try {
                // root
                archive.putArchiveEntry(entryFactory.apply(rootName));
                archive.closeArchiveEntry();

                // bin folder
                archive.putArchiveEntry(entryFactory.apply(rootName + "bin/"));
                archive.closeArchiveEntry();
            } catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        };
        final byte[] guContent = "works".getBytes(StandardCharsets.UTF_8);
        if ("cygwin".equals(platform)) { // zip
            try (final ZipArchiveOutputStream archive = new ZipArchiveOutputStream(outputStream)) {
                prepareStructure.accept(archive, ZipArchiveEntry::new);

                final ZipArchiveEntry gu = new ZipArchiveEntry(rootName + "bin/gu");
                gu.setSize(guContent.length);
                archive.putArchiveEntry(gu);
                archive.write(guContent);
                archive.closeArchiveEntry();
            }
        } else { // tar.gz
            try (final TarArchiveOutputStream archive = new TarArchiveOutputStream(new GzipCompressorOutputStream(outputStream))) {
                prepareStructure.accept(archive, TarArchiveEntry::new);

                final TarArchiveEntry gu = new TarArchiveEntry(rootName + "bin/gu");
                gu.setSize(guContent.length);
                archive.putArchiveEntry(gu);
                archive.write(guContent);
                archive.closeArchiveEntry();
            }
        }
        return outputStream;
    }

    private SdkmanGraalVMInstaller newInstaller(final Path workdir, final String platform, final String version,
                                                final String baseUrl, final SpiedInstaller installer, final SpiedResolver resolver) {
        return new SdkmanGraalVMInstaller(
                SdkmanGraalVMInstallerConfiguration.builder()
                .offline(false)
                .inheritIO(true)
                .url(baseUrl + "/broker/download/java/" + version + "/" + platform)
                .version(version)
                .platform(platform)
                .gav("org.apache.geronimo.arthur.cache:graal:" + ("cygwin".equals(platform) ? "zip" : "tar.gz") + ":" + platform + ':' + version)
                .workdir(workdir)
                .resolver(resolver::resolve)
                .installer(installer::install)
                .extractor(new Extractor()::unpack)
                .build());
    }

    public static class SpiedInstaller {
        private final AtomicInteger counter = new AtomicInteger();

        public Path install(final String gav, final Path source) {
            counter.incrementAndGet();
            return source;
        }
    }

    public static class SpiedResolver {
        private final AtomicInteger counter = new AtomicInteger();
        private Path result;

        public Path resolve(final String gav) {
            counter.incrementAndGet();
            if (result != null) {
                return result;
            }
            throw new IllegalStateException("missing in the test");
        }
    }
}

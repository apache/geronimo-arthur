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
package org.apache.geronimo.arthur.documentation.command;

import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static org.asciidoctor.SafeMode.UNSAFE;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.geronimo.arthur.documentation.io.FolderConfiguration;
import org.apache.geronimo.arthur.documentation.io.FolderVisitor;
import org.apache.geronimo.arthur.documentation.mojo.MojoParser;
import org.apache.geronimo.arthur.documentation.renderer.AsciidocRenderer;
import org.apache.geronimo.arthur.documentation.renderer.TemplateConfiguration;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Defaults.DefaultMapping;
import org.tomitribe.crest.api.Err;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;

public class Generate {
    @Command
    public void generate(
            @DefaultMapping(name = "location", value = "src/content")
            @DefaultMapping(name = "includes", value = "[.+/]?.+\\.adoc$")
            @Option("content-") final FolderConfiguration contentConfiguration,

            @DefaultMapping(name = "location", value = "src/static")
            @Option("static-") final FolderConfiguration staticConfiguration,

            @DefaultMapping(name = "header", value = "src/template/header.html")
            @DefaultMapping(name = "footer", value = "src/template/footer.html")
            @DefaultMapping(name = "nav", value = "src/template/nav.adoc")
            @Option("template-") final TemplateConfiguration templateConfiguration,

            @Option("mojo") final List<Path> mojos,
            @Option("output") final Path output,
            @Option("work-directory") final Path workdir,

            @Option("threads") @Default("${sys.processorCount}") final int threads,

            @Out final PrintStream stdout,
            @Err final PrintStream stderr,

            final AsciidocRenderer renderer,
            final FolderVisitor visitor,
            final MojoParser mojoParser) {
        stdout.println("Generating the website in " + output);

        final Collection<Throwable> errors = new ArrayList<>();
        final Executor executorImpl = threads > 1 ? newThreadPool(output, threads) : Runnable::run;
        final Executor executor = task -> {
            try {
                task.run();
            } catch (final Throwable err) {
                err.printStackTrace(stderr);
                synchronized (errors) {
                    errors.add(err);
                }
            }
        };

        final CountDownLatch templateLatch = new CountDownLatch(1);
        final AtomicReference<BiFunction<String, String, String>> computedTemplatization = new AtomicReference<>();
        final Supplier<BiFunction<String, String, String>> templatize = () -> {
            try {
                templateLatch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return computedTemplatization.get();
        };

        final Options adocOptions = OptionsBuilder.options()
                .safe(UNSAFE) // we generated_dir is not safe but locally it is ok
                .attributes(AttributesBuilder.attributes()
                    .showTitle(true)
                    .icons("font")
                    .attribute("generated_dir", workdir.toAbsolutePath().toString()))
                .get();

        executor.execute(() -> {
            try {
                computedTemplatization.set(compileTemplate(templateConfiguration));
            } finally {
                templateLatch.countDown();
            }
        });
        executor.execute(() -> mojos.forEach(mojo -> {
            try {
                generateMojoDoc(
                        mojo.getFileName().toString().toLowerCase(ROOT).replace("mojo.java", ""),
                        mojoParser, mojo, workdir, stdout);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }));

        final CountDownLatch visitorsFinished = new CountDownLatch(2);
        try {
            executor.execute(() -> {
                try {
                    visitor.visit(contentConfiguration, file -> executor.execute(() -> {
                        final String name = file.getFileName().toString();
                        final int dot = name.lastIndexOf('.');
                        final String targetFilename = dot > 0 ? name.substring(0, dot) + ".html" : name;
                        final Path targetFolder = contentConfiguration.getLocation()
                                .relativize(file)
                                .getParent();
                        final Path target = output.resolve(targetFolder == null ?
                                Paths.get(targetFilename) :
                                targetFolder.resolve(targetFilename));
                        ensureExists(target.getParent());
                        try {
                            final String read = read(file);
                            final Map<String, String> metadata = renderer.extractMetadata(read);
                            Files.write(
                                    target,
                                    templatize.get().apply(
                                            metadata.getOrDefault("title", "Arthur"),
                                            renderer.render(read, adocOptions))
                                            .getBytes(StandardCharsets.UTF_8),
                                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        stdout.println("Created '" + target + "'");
                    }));
                } finally {
                    visitorsFinished.countDown();
                }
            });
            executor.execute(() -> {
                try {
                    visitor.visit(staticConfiguration, file -> executor.execute(() -> {
                        final Path target = output.resolve(staticConfiguration.getLocation().relativize(file));
                        ensureExists(target.getParent());
                        final String filename = file.getFileName().toString();
                        try {
                            if (filename.endsWith(".js") || filename.endsWith(".css")) { // strip ASF header
                                try (final BufferedReader reader = Files.newBufferedReader(file);
                                     final InputStream stream = new ByteArrayInputStream(
                                             reader.lines().skip(16/*header*/).collect(joining("\n")).getBytes(StandardCharsets.UTF_8))) {
                                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } else {
                                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        stdout.println("Copied '" + target + "'");
                    }));
                } finally {
                    visitorsFinished.countDown();
                }
            });
        } finally {
            try {
                visitorsFinished.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ExecutorService.class.isInstance(executorImpl)) {
                final ExecutorService service = ExecutorService.class.cast(executorImpl);
                service.shutdown();
                try {
                    if (!service.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                        stderr.println("Exiting without the executor being properly shutdown");
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (!errors.isEmpty()) {
            final IllegalStateException failed = new IllegalStateException("Execution failed");
            errors.forEach(failed::addSuppressed);
            throw failed;
        }

        stdout.println("Website generation done");
    }

    private void generateMojoDoc(final String marker, final MojoParser mojoParser, final Path mojo, final Path workdir,
                                 final PrintStream stdout) throws IOException {
        ensureExists(workdir);
        final Collection<MojoParser.Parameter> parameters = mojoParser.extractParameters(mojo);
        try (final Writer writer = Files.newBufferedWriter(workdir.resolve("generated_" + marker + "_mojo.adoc"))) {
            writer.write("[opts=\"header\",role=\"table table-bordered\",cols=\"2,1,3\"]\n" +
                    "|===\n" +
                    "|Name|Type|Description\n\n" +
                    parameters.stream()
                        .sorted(comparing(MojoParser.Parameter::getName))
                        .map(this::toLine)
                        .collect(joining("\n\n")) +
                    "\n|===\n");
        }
        stdout.println("Generated documentation for " + mojo);
    }

    private String toLine(final MojoParser.Parameter parameter) {
        return "|" + parameter.getName() + (parameter.isRequired() ? "*" : "") +
                "\n|" + parameter.getType() +
                "\na|\n" + parameter.getDescription() +
                "\n\n*Default value*: " + parameter.getDefaultValue() +
                "\n\n*User property*: " + parameter.getProperty();
    }

    private String read(final Path file) {
        try {
            return Files.lines(file).collect(joining("\n"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private BiFunction<String, String, String> compileTemplate(final TemplateConfiguration templateConfiguration) {
        final Collection<Function<String, String>> parts = new ArrayList<>();

        final Path header = templateConfiguration.getHeader();
        if (header != null && Files.exists(header)) {
            final String content = read(header);
            parts.add(s -> content + s);
        }

        final Path footer = templateConfiguration.getFooter();
        if (footer != null && Files.exists(footer)) {
            final String content = read(footer);
            parts.add(s -> s + content);
        }
        final Function<String, String> fn = parts.stream().reduce(identity(), Function::andThen);
        return (title, html) -> fn.apply(html).replace("${arthurTemplateTitle}", title);
    }

    private ExecutorService newThreadPool(@Option("output") Path output, @Default("${sys.processorCount}") @Option("threads") int threads) {
        return new ThreadPoolExecutor(threads, threads, 1, MINUTES, new LinkedBlockingQueue<>(), new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable worker) {
                return new Thread(worker, "arthur-generator-" + counter.incrementAndGet() + "-[" + output + "]");
            }
        });
    }

    private void ensureExists(final Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}

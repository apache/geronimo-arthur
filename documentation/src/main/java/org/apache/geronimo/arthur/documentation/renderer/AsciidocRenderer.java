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
package org.apache.geronimo.arthur.documentation.renderer;

import static java.util.Collections.singletonMap;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.DocumentHeader;
import org.asciidoctor.jruby.internal.JRubyAsciidoctor;

public class AsciidocRenderer implements AutoCloseable {
    private Asciidoctor asciidoctor;

    private final CountDownLatch latch = new CountDownLatch(1);

    public AsciidocRenderer() {
        new Thread(() -> { // this is insanely slow so let's do it in background
            asciidoctor = JRubyAsciidoctor.create();
            latch.countDown();
        }, getClass().getName() + '-' + hashCode()).start();
    }

    public String render(final String input, final Options options) {
        await();
        return asciidoctor.convert(input, options);
    }

    @Override
    public void close() {
        await();
        asciidoctor.shutdown();
    }

    private void await() {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, String> extractMetadata(final String content) {
        await();
        final DocumentHeader header = asciidoctor.readDocumentHeader(content);
        return singletonMap("title", header.getPageTitle());
    }
}

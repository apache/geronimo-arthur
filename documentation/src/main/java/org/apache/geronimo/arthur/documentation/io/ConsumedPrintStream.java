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
package org.apache.geronimo.arthur.documentation.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class ConsumedPrintStream extends PrintStream {
    private final Consumer<String> consumer;

    private final ByteArrayOutputStream buffer;

    public ConsumedPrintStream(final Consumer<String> consumer) {
        super(new ByteArrayOutputStream());
        this.consumer = consumer;
        this.buffer = ByteArrayOutputStream.class.cast(out);
    }

    private void onEnd() {
        final byte[] bytes = buffer.toByteArray();
        if (bytes.length > 0) {
            consumer.accept(new String(bytes, 0, bytes[bytes.length - 1] == '\n' ? bytes.length - 1 : bytes.length, StandardCharsets.UTF_8));
            buffer.reset();
        }
    }

    @Override
    public void println(final String content) {
        super.println(content);
        onEnd();
    }

    @Override
    public void println() {
        super.println();
        onEnd();
    }

    @Override
    public void println(final boolean x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final char x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final int x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final long x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final float x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final double x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final char[] x) {
        super.println(x);
        onEnd();
    }

    @Override
    public void println(final Object x) {
        super.println(x);
        onEnd();
    }
}

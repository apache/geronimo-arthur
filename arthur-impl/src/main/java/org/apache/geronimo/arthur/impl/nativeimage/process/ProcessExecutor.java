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
package org.apache.geronimo.arthur.impl.nativeimage.process;

import java.io.IOException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProcessExecutor implements Runnable {
    private final boolean inheritIO;
    private final List<String> command;

    @Override
    public void run() {
        if (log.isDebugEnabled()) {
            log.debug("Launching {}", command);
        }

        Process process = null;
        try {
            final ProcessBuilder builder = new ProcessBuilder(command);
            if (inheritIO) {
                builder.inheritIO();
            }
            process = builder.start();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalArgumentException("Invalid exit code: " + exitCode);
            }
        } catch (final InterruptedException e) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

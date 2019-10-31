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
package org.apache.geronimo.arthur.integrationtests.container;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.testcontainers.containers.GenericContainer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MavenContainer extends GenericContainer<MavenContainer> {
    public MavenContainer() {
        super("maven:3.6.2-jdk-8-slim");
        setWorkingDirectory("/opt/geronimo/arthur/integration-test");
        setCommand("sleep", "infinity");
        withFileSystemBind(System.getProperty("arthur.m2.repository"), "/root/.m2/repository");
    }

    @Override
    public void start() {
        super.start();
        try {
            log.info("Ensuring gcc is present");

            final ExecResult update = execInContainer("apt", "update");
            assertEquals(0, update.getExitCode(), () -> "Can't update apt: " + update.getStderr());

            final ExecResult gcc = execInContainer("apt", "install", "-y", "gcc", "libc6-dev", "zlib1g-dev");
            assertEquals(0, gcc.getExitCode(), () -> "Can't install gcc: " + gcc.getStderr());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

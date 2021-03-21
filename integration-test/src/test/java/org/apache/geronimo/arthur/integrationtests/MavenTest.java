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
package org.apache.geronimo.arthur.integrationtests;

import org.apache.geronimo.arthur.integrationtests.container.MavenContainer;
import org.apache.geronimo.arthur.integrationtests.junit5.Invocation;
import org.apache.geronimo.arthur.integrationtests.junit5.Spec;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.BuiltinUserAuthFactories;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
class MavenTest {
    @Container
    private static final MavenContainer MVN = new MavenContainer();

    @Test
    @Spec(expectedOutput = "Cui-yère")
    void cuilliere() {}

    @Test
    @Spec(expectedOutput = "counter=1, from proxy=from proxy")
    void owb() {}

    @Test
    @Spec(expectedOutput = "Starting org.apache.geronimo.arthur.integrationtests.Application")
    void scr() {}

    @Test
    @Spec(expectedOutput = "" +
            "[main] INFO org.apache.geronimo.arthur.integrationtests.OpenJPAMain" +
            " - findbyid => root:id=1,name=root_1,children=[child:id=2,name=child_2, child:id=1,name=child_1]\n" +
            "[main] INFO org.apache.geronimo.arthur.integrationtests.OpenJPAMain" +
            " - criteria builder => root:id=1,name=root_1,children=[child:id=2,name=child_2, child:id=1,name=child_1]")
    void openjpa() {}

    @Test
    @Spec(expectedOutput = "pong", forwardedExecutionSystemProperties = {
            "MavenTest.jsch.port", "java.library.path", "javax.net.ssl.trustStore"
    })
    void jsch(final Invocation invocation) {
        final SshServer ssh = SshServer.setUpDefaultServer();
        ssh.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get("target/missing")));
        ssh.setPort(0);
        ssh.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ssh.setUserAuthFactories(singletonList(BuiltinUserAuthFactories.PASSWORD.create()));
        ssh.setPasswordAuthenticator((username, password, session) -> Objects.equals("test", username) && Objects.equals("testpwd", password));
        ssh.setCommandFactory((channel, command) -> {
            if ("ping".equals(command)) {
                return new AbstractCommandSupport("ping", null) {
                    @Override
                    public void run() {
                        try {
                            getOutputStream().write("pong".getBytes(StandardCharsets.UTF_8));
                            getExitCallback().onExit(0);
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                };
            }
            throw new IllegalArgumentException(command);
        });

        try {
            ssh.start();
            System.setProperty("MavenTest.jsch.port", Integer.toString(ssh.getPort()));
            invocation.run();
        } catch (final IOException e) {
            fail(e);
        } finally {
            System.clearProperty("MavenTest.jsch.port");
            if (ssh.isStarted()) {
                try {
                    ssh.close(true).await();
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

    }
}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public final class JschMain {
    private JschMain() {
        // noop
    }

    public static void main(final String[] args) {
        final int port = Integer.getInteger("MavenTest.jsch.port");
        Session session;
        try {
            session = new JSch().getSession("test", "localhost", port);
            session.setPassword("testpwd");
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.connect();
        } catch (final JSchException e) {
            throw new IllegalStateException(e);
        }

        try {
            final ChannelExec channelExec = ChannelExec.class.cast(session.openChannel("exec"));
            channelExec.setCommand("ping");
            channelExec.setInputStream(System.in, true);
            channelExec.setOutputStream(System.out, true);
            channelExec.setErrStream(System.err, true);
            channelExec.connect();

            final InputStream inputStream = channelExec.getInputStream();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int length;
            while (channelExec.isConnected() && (length = inputStream.read(buffer)) != -1) {
                System.out.write(buffer, 0, length);
            }
        } catch (final JSchException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            session.disconnect();
        }
    }
}

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
 */package org.apache.geronimo.arthur.maven.mojo;

import static java.util.Optional.ofNullable;

import java.util.Optional;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.RegistryImage;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/**
 * Alternate mojo to jib:build to avoid to bundle useless files.
 * Can be replaced by vanilla jib when it will support it, see https://github.com/GoogleContainerTools/jib/issues/1857
 */
@Mojo(name = "image", threadSafe = true)
public class ImageMojo extends JibMojo {
    /**
     * Server identifier (in settings.xml) used to authenticate to the remote image registry.
     */
    @Parameter(property = "arthur.serverId")
    private String serverId;

    @Component
    private SettingsDecrypter settingsDecrypter;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Override
    protected Containerizer createContainer() throws InvalidImageReferenceException {
        final ImageReference reference = ImageReference.parse(to);
        final RegistryImage image = RegistryImage.named(reference);
        registerCredentials(reference, image);
        return Containerizer.to(image);
    }

    private void registerCredentials(final ImageReference reference, final RegistryImage registryImage) {
        ofNullable(serverId)
            .map(Optional::of)
            .orElseGet(() -> ofNullable(reference.getRegistry()))
            .map(id -> session.getSettings().getServer(id))
            .map(it -> settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(it)))
            .map(SettingsDecryptionResult::getServer)
            .ifPresent(server -> registryImage.addCredential(server.getUsername(), server.getPassword()));
    }
}

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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MavenContainer extends GenericContainer<MavenContainer> {
    public MavenContainer() {
        super(findImage());
        setWorkingDirectory("/opt/geronimo/arthur/integration-test");
        setCommand("sleep", "infinity");
        withFileSystemBind(System.getProperty("arthur.m2.repository"), "/root/.m2/repository"); // cache
        // enable to start a server in a test and connect on it from the mvn container
        setNetworkMode(System.getProperty("arthur.container.maven.network", "host"));
    }

    private static String findImage() {
        return Optional.of(System.getProperty("arthur.container.maven.image", "auto"))
                .filter(it -> !"auto".equals(it))
                .orElseGet(MavenContainer::getOrCreateAutoBaseImage);
    }

    // we can run apt update && apt install -y gcc libc6-dev zlib1g-dev in start() but it is slow so we cache it through an image
    // note: we don't clean the image to be able to reuse it and speed up integration-tests, use -Darthur.container.maven.deleteOnExit=true to auto clean it
    private static String getOrCreateAutoBaseImage() {
        final String fromImage = System.getProperty("arthur.container.maven.baseimage", "maven:3.6.3-jdk-8-slim");
        // creating a tag from the source image to ensure we can have multiple test versions (maven/jdk matrix)
        final String tag = fromImage.split(":")[1];
        final String targetImage = "apache/geronimo/arthur/maven-test-base:" + tag;

        final DockerClient client = DockerClientFactory.instance().client();
        try {
            // note that docker daemon can ignore filter parameter so let's check it exactly
            final List<Image> images = client.listImagesCmd()
                    .withLabelFilter("org.apache.geronimo.arthur.tag=" + tag)
                    .withDanglingFilter(false)
                    .exec();
            if (images.size() == 1) {
                log.info("Found '{}' image, reusing it", targetImage);
                return targetImage;
            }

            log.info("Didn't find '{}', creating it from '{}'", targetImage, fromImage);
            return new ImageFromDockerfile(
                    targetImage, Boolean.getBoolean("arthur.container.maven.deleteOnExit"))
                    .withDockerfileFromBuilder(builder -> builder.from(fromImage)
                            .run("apt update && apt install -y gcc g++ libc6-dev zlib1g-dev")
                            .label("org.apache.geronimo.arthur.environment", "integration-tests")
                            .label("org.apache.geronimo.arthur.baseImage", fromImage)
                            .label("org.apache.geronimo.arthur.tag", tag))
                    .get();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Alternate mojo to jib:dockerBuild to avoid to bundle useless files.
 * Can be replaced by vanilla jib when it will support it, see https://github.com/GoogleContainerTools/jib/issues/1857
 */
@Mojo(name = "docker", threadSafe = true)
public class DockerMojo extends JibMojo {
    @Override
    protected Containerizer createContainer() throws InvalidImageReferenceException {
        return Containerizer.to(DockerDaemonImage.named(this.to));
    }
}

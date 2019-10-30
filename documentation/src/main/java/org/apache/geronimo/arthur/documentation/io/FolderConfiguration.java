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

import java.nio.file.Path;
import java.util.List;

import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import lombok.Data;

@Data
@Options
public class FolderConfiguration {
    private final Path location;
    private final List<String> includes;
    private final List<String> excludes;

    public FolderConfiguration(@Option("location") final Path location,
                               @Option("includes") final List<String> includes,
                               @Option("excludes") @Default("^\\..+") final List<String> excludes) {
        this.location = location;
        this.includes = includes;
        this.excludes = excludes;
    }
}
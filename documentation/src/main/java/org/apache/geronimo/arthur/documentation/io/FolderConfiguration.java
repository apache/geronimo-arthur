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

@Options
public class FolderConfiguration {
    private final Path location;
    private final List<String> includes;
    private final List<String> excludes;

    public FolderConfiguration(@Option("location") final Path location, @Option("includes") final List<String> includes, @Option("excludes") @Default("^\\..+") final List<String> excludes) {
        this.location = location;
        this.includes = includes;
        this.excludes = excludes;
    }

    public Path getLocation() {
        return this.location;
    }

    public List<String> getIncludes() {
        return this.includes;
    }

    public List<String> getExcludes() {
        return this.excludes;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof FolderConfiguration)) return false;
        final FolderConfiguration other = (FolderConfiguration) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$location = this.getLocation();
        final java.lang.Object other$location = other.getLocation();
        if (this$location == null ? other$location != null : !this$location.equals(other$location)) return false;
        final java.lang.Object this$includes = this.getIncludes();
        final java.lang.Object other$includes = other.getIncludes();
        if (this$includes == null ? other$includes != null : !this$includes.equals(other$includes)) return false;
        final java.lang.Object this$excludes = this.getExcludes();
        final java.lang.Object other$excludes = other.getExcludes();
        if (this$excludes == null ? other$excludes != null : !this$excludes.equals(other$excludes)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof FolderConfiguration;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $location = this.getLocation();
        result = result * PRIME + ($location == null ? 43 : $location.hashCode());
        final java.lang.Object $includes = this.getIncludes();
        result = result * PRIME + ($includes == null ? 43 : $includes.hashCode());
        final java.lang.Object $excludes = this.getExcludes();
        result = result * PRIME + ($excludes == null ? 43 : $excludes.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "FolderConfiguration(location=" + this.getLocation() + ", includes=" + this.getIncludes() + ", excludes=" + this.getExcludes() + ")";
    }
}

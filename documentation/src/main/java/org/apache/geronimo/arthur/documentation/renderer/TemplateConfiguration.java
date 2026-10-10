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
package org.apache.geronimo.arthur.documentation.renderer;

import java.nio.file.Path;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

@Options
public class TemplateConfiguration {
    private final Path header;
    private final Path footer;

    public TemplateConfiguration(@Option("header") final Path header, @Option("footer") final Path footer) {
        this.header = header;
        this.footer = footer;
    }

    public Path getHeader() {
        return this.header;
    }

    public Path getFooter() {
        return this.footer;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof TemplateConfiguration)) return false;
        final TemplateConfiguration other = (TemplateConfiguration) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$header = this.getHeader();
        final java.lang.Object other$header = other.getHeader();
        if (this$header == null ? other$header != null : !this$header.equals(other$header)) return false;
        final java.lang.Object this$footer = this.getFooter();
        final java.lang.Object other$footer = other.getFooter();
        if (this$footer == null ? other$footer != null : !this$footer.equals(other$footer)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof TemplateConfiguration;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $header = this.getHeader();
        result = result * PRIME + ($header == null ? 43 : $header.hashCode());
        final java.lang.Object $footer = this.getFooter();
        result = result * PRIME + ($footer == null ? 43 : $footer.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "TemplateConfiguration(header=" + this.getHeader() + ", footer=" + this.getFooter() + ")";
    }
}

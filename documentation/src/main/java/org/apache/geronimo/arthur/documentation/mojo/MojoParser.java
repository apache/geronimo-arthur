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
package org.apache.geronimo.arthur.documentation.mojo;

import static java.util.stream.Collectors.toList;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

public class MojoParser {
    // simplified java parser for *our* code
    public Collection<Parameter> extractParameters(final Path file) throws IOException {
        final Collection<Parameter> parameters = new ArrayList<>();
        String parent = null;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            boolean jdoc = false;
            final StringBuilder javadoc = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("public class ") || line.startsWith("public abstract class ")) {
                    final int ext = line.indexOf("extends ");
                    if (ext > 0) {
                        parent = line.substring(ext + "extends ".length(), line.indexOf("{")).trim();
                    }
                    if ("AbstractMojo".equals(parent)) {
                        parent = null;
                    }
                } else if (line.startsWith("/**")) {
                    jdoc = true;
                } else if (line.endsWith("*/")) {
                    jdoc = false;
                } else if (jdoc) {
                    javadoc.append(line.startsWith("*") ? line.substring(1).trim() : line);
                } else if (javadoc.length() > 0 && line.startsWith("@Parameter")) {
                    final StringBuilder annotation = new StringBuilder(line);
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("private ") || line.startsWith("protected ")) {
                            final int nameStart = line.lastIndexOf(' ') + 1;
                            final String config = annotation.toString();
                            final int prefixLen = line.indexOf(' ') + 1;
                            String type = line.substring(prefixLen, line.indexOf(" ", prefixLen)).trim();
                            final int generics = type.indexOf('<');
                            if (generics > 0) {
                                type = type.substring(0, generics);
                            }
                            type = type.substring(type.indexOf('.') + 1); // nested classes dont need enclosing
                            parameters.add(new Parameter(line.substring(nameStart, line.length() - ";".length()), type, config.contains("required = true"), find("property =", config), javadoc.toString(), find("defaultValue =", config)));
                            javadoc.setLength(0);
                            break;
                        } else {
                            annotation.append(line);
                        }
                    }
                } else if (line.startsWith("package")) {
                    // we have the header before
                    javadoc.setLength(0);
                }
            }
        }
        if (parent != null) {
            return Stream.concat(parameters.stream(), extractParameters(file.getParent().resolve(parent + ".java")).stream()).collect(toList());
        }
        return parameters;
    }

    private String find(final String prefix, final String value) {
        int start = value.indexOf(prefix);
        if (start < 0) {
            return "-";
        }
        start += prefix.length();
        while (Character.isWhitespace(value.charAt(start)) || value.charAt(start) == '\"') {
            start++;
        }
        final int end = value.indexOf('\"', start);
        if (end > 0) {
            return value.substring(start, end).trim();
        }
        return "-";
    }


    public static class Parameter {
        private final String name;
        private final String type;
        private final boolean required;
        private final String property;
        private final String description;
        private final String defaultValue;

        public Parameter(final String name, final String type, final boolean required, final String property, final String description, final String defaultValue) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.property = property;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return this.name;
        }

        public String getType() {
            return this.type;
        }

        public boolean isRequired() {
            return this.required;
        }

        public String getProperty() {
            return this.property;
        }

        public String getDescription() {
            return this.description;
        }

        public String getDefaultValue() {
            return this.defaultValue;
        }

        @java.lang.Override
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof MojoParser.Parameter)) return false;
            final MojoParser.Parameter other = (MojoParser.Parameter) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            if (this.isRequired() != other.isRequired()) return false;
            final java.lang.Object this$name = this.getName();
            final java.lang.Object other$name = other.getName();
            if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
            final java.lang.Object this$type = this.getType();
            final java.lang.Object other$type = other.getType();
            if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
            final java.lang.Object this$property = this.getProperty();
            final java.lang.Object other$property = other.getProperty();
            if (this$property == null ? other$property != null : !this$property.equals(other$property)) return false;
            final java.lang.Object this$description = this.getDescription();
            final java.lang.Object other$description = other.getDescription();
            if (this$description == null ? other$description != null : !this$description.equals(other$description)) return false;
            final java.lang.Object this$defaultValue = this.getDefaultValue();
            final java.lang.Object other$defaultValue = other.getDefaultValue();
            if (this$defaultValue == null ? other$defaultValue != null : !this$defaultValue.equals(other$defaultValue)) return false;
            return true;
        }

        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof MojoParser.Parameter;
        }

        @java.lang.Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            result = result * PRIME + (this.isRequired() ? 79 : 97);
            final java.lang.Object $name = this.getName();
            result = result * PRIME + ($name == null ? 43 : $name.hashCode());
            final java.lang.Object $type = this.getType();
            result = result * PRIME + ($type == null ? 43 : $type.hashCode());
            final java.lang.Object $property = this.getProperty();
            result = result * PRIME + ($property == null ? 43 : $property.hashCode());
            final java.lang.Object $description = this.getDescription();
            result = result * PRIME + ($description == null ? 43 : $description.hashCode());
            final java.lang.Object $defaultValue = this.getDefaultValue();
            result = result * PRIME + ($defaultValue == null ? 43 : $defaultValue.hashCode());
            return result;
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "MojoParser.Parameter(name=" + this.getName() + ", type=" + this.getType() + ", required=" + this.isRequired() + ", property=" + this.getProperty() + ", description=" + this.getDescription() + ", defaultValue=" + this.getDefaultValue() + ")";
        }
    }
}

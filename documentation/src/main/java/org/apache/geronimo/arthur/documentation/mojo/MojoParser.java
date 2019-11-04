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

import lombok.Data;

public class MojoParser {
    // simplified java parser for *our* code
    public Collection<Parameter> extractParameters(final Path file) throws IOException {
        final Collection<Parameter> parameters = new ArrayList<>();
        String parent = null;
        try (final BufferedReader reader = Files.newBufferedReader(file)) {
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
                            parameters.add(new Parameter(
                                    line.substring(nameStart, line.length() - ";".length()),
                                    type,
                                    config.contains("required = true"),
                                    find("property =", config),
                                    javadoc.toString(),
                                    find("defaultValue =", config)));
                            javadoc.setLength(0);
                            break;
                        } else {
                            annotation.append(line);
                        }
                    }
                } else if (line.startsWith("package")) { // we have the header before
                    javadoc.setLength(0);
                }
            }
        }
        if (parent != null) {
            return Stream.concat(
                    parameters.stream(),
                    extractParameters(file.getParent().resolve(parent + ".java")).stream())
                    .collect(toList());
        }
        return parameters;
    }

    private String find(final String prefix, final String value) {
        int start = value.indexOf(prefix);
        if (start < 0) {
            return "-";
        }
        start += prefix.length();
        while (Character.isWhitespace(value.charAt(start)) || value.charAt(start) == '"') {
            start++;
        }
        final int end = value.indexOf('"', start);
        if (end > 0) {
            return value.substring(start, end).trim();
        }
        return "-";
    }

    @Data
    public static class Parameter {
        private final String name;
        private final String type;
        private final boolean required;
        private final String property;
        private final String description;
        private final String defaultValue;
    }
}

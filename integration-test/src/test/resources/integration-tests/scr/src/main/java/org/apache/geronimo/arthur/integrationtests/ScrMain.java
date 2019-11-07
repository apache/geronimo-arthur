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

import java.io.IOException;

import org.apache.winegrower.Ripener;

public final class ScrMain {
    private ScrMain() {
        // noop
    }

    // strictly speaking we could just use Ripener.main(String[]) but for an IT it is weird to not have any entry point
    //
    // side note: to debug the binary run:
    // $ ./target/scr.graal.bin -Dorg.slf4j.simpleLogger.logFile=System.out -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
    public static void main(final String[] args) throws IOException {
        setIfMissing("org.slf4j.simpleLogger.logFile", "System.out");
        setIfMissing("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
        setIfMissing("ds.service.changecount.timeout", "1"); // otherwise shutdown will wait 5s for nothing here

        final Ripener.Configuration configuration = new Ripener.Configuration();
        configuration.setJarFilter(it -> true); // we built the metadata so no scanning
        try (final Ripener ripener = new Ripener.Impl(configuration).start()) {
            // no-op, deployment will print "Starting org.apache.geronimo.arthur.integrationtests.Application"
        }
    }

    private static void setIfMissing(final String key, final String value) {
        System.setProperty(key, System.getProperty(key, value));
    }
}

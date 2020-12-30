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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OWB {
    private OWB() {
        // noop
    }

    public static void main(final String[] args) throws IOException {
        Logger.getLogger("org.apache").setLevel(Level.WARNING);
        try (final SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            // starter is launched automatically
            final int counter = container.select(Starter.class).get().getCounter();
            if (counter != 1) {
                throw new IllegalStateException("Starter didn't start: " + counter);
            }

            final Proxied proxied = container.select(Proxied.class).get();
            final String proxyValue = proxied.getAnything();
            if (!"from proxy".equals(proxyValue)) {
                throw new IllegalStateException(proxied + ": " + proxyValue);
            }
            System.out.println("counter=" + counter + ", from proxy=" + proxyValue);
        }
    }

    private static void setIfMissing(final String key, final String value) {
        System.setProperty(key, System.getProperty(key, value));
    }

    @ApplicationScoped
    public static class Starter {
        private int counter = 0;

        public int getCounter() {
            return counter;
        }

        public void onStart(@Observes @Initialized(ApplicationScoped.class) final Object start,
                            final Proxied proxied) {
            counter++;
            Logger.getLogger(getClass().getName() + // who
                    " started: proxy_class=" + proxied.getClass().getName() + ", " + // uses proxy class
                    proxied.getAnything()); // proxy works
        }
    }

    @ApplicationScoped
    public static class Proxied {
        public String getAnything() {
            return "from proxy";
        }
    }
}

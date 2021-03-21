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
package org.apache.geronimo.arthur.knight.hsqldb;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

// don't forget to set -Dhsqldb.reconfig_logging=false in main or runtime
public class HsqldbExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        registerReflection(context);
        registerResourceBundles(context);
        resourceResources(context);
    }

    private void resourceResources(final Context context) {
        context.register(new ResourceModel("org\\/hsqldb\\/resources\\/.+.sql"));
    }

    private void registerResourceBundles(final Context context) {
        context.register(new ResourceBundleModel("org.hsqldb.resources.info-column-remarks"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.info-table-remarks"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.org_hsqldb_DatabaseClassLoader"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.org_hsqldb_server_Server_messages"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.sql-state-messages"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.webserver-content-types"));
        context.register(new ResourceBundleModel("org.hsqldb.resources.webserver-pages"));
        context.register(new ResourceBundleModel("org.hsqldb.lib.tar.rb"));
    }

    private void registerReflection(final Context context) {
        context.register(new ClassReflectionModel("org.hsqldb.jdbcDriver", null, true, null, null, null, null, null, null, null, null));
        context.register(new ClassReflectionModel("org.hsqldb.dbinfo.DatabaseInformationFull", true, null, null, null, null, null, null, null, null, null));
        context.register(new ClassReflectionModel("org.hsqldb.dbinfo.DatabaseInformationMain", true, null, null, null, null, null, null, null, null, null));
    }
}

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
package org.apache.geronimo.arthur.knight.openjpa;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.stream.Stream;

public class OpenJPAExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        registerJPAClasses(context);
        registerSPI(context);
        registerI18n(context);
        registerDictionaryResources(context);
        registerPrimitiveWrappers(context);
        registerBuildTimeProxies(context);
        registerEnumForValuesReflection(context);
        registerDBCP2IfPresent(context);
        context.register(new ResourceModel("META-INF\\/org\\.apache\\.openjpa\\.revision\\.properties"));
        context.register(new ResourceBundleModel("org.apache.openjpa.jdbc.schema.localizer"));
    }

    private void registerEnumForValuesReflection(final Context context) {
        context.register(new ClassReflectionModel("org.apache.openjpa.persistence.jdbc.FetchMode", null, null, null, true, null, null, null, null, null, null));
        context.register(new ClassReflectionModel("org.apache.openjpa.persistence.jdbc.FetchDirection", null, null, null, true, null, null, null, null, null, null));
        context.register(new ClassReflectionModel("org.apache.openjpa.persistence.jdbc.JoinSyntax", null, null, null, true, null, null, null, null, null, null));
    }

    private void registerBuildTimeProxies(final Context context) {
        Stream.of(
                "java.sql.Date", "java.sql.Time", "java.sql.Timestamp",
                "java.util.ArrayList", "java.util.Date", "java.util.EnumMap",
                "java.util.GregorianCalendar", "java.util.HashMap", "java.util.HashSet",
                "java.util.Hashtable", "java.util.IdentityHashMap", "java.util.LinkedHashMap", "java.util.LinkedHashSet",
                "java.util.LinkedList", "java.util.PriorityQueue", "java.util.Properties", "java.util.TreeMap",
                "java.util.TreeSet", "java.util.Vector")
                .map(it -> "org.apache.openjpa.util." + it.replace('.', '$') + "$proxy")
                .map(it -> new ClassReflectionModel(it, null, true, null, null, null, null, null, null, null, null))
                .forEach(context::register);
    }

    // see Options class (stringToObject method)
    private void registerPrimitiveWrappers(final Context context) {
        Stream.of(Boolean.class, Byte.class, Character.class, Double.class, Float.class, Integer.class, Long.class, Short.class)
                .forEach(it -> context.register(new ClassReflectionModel(it.getName(), null, true, null, true /*valueOf*/, null, null, null, null, null, null)));
    }

    private void registerDictionaryResources(final Context context) {
        context.register(new ResourceModel("org\\/apache\\/openjpa\\/jdbc\\/sql\\/sql-keywords\\.rsrc"));
        context.register(new ResourceModel("org\\/apache\\/openjpa\\/jdbc\\/sql\\/sql-error-state-codes\\.xml"));
    }

    // todo: dbcp2-knight and inherit from it automatically?
    private void registerDBCP2IfPresent(final Context context) {
        try {
            final Class<?> evictionPolicy = context.loadClass("org.apache.commons.pool2.impl.DefaultEvictionPolicy");

            final ClassReflectionModel model = new ClassReflectionModel();
            model.setName(evictionPolicy.getName());
            model.setAllPublicConstructors(true);
            context.register(model);

            context.register(new ResourceBundleModel("org.apache.commons.dbcp2.LocalStrings"));

            // dbcp2 depends on commons-logging in a hardcoded way (don't ask)
            addCommonsLogging(context).forEach(it -> {
                final ClassReflectionModel reflect = new ClassReflectionModel();
                reflect.setName(it.getName());
                reflect.setAllPublicConstructors(true);
                reflect.setAllDeclaredConstructors(true);
                context.register(reflect);
            });
        } catch (final NoClassDefFoundError | Exception e) {
            // no-op
        }
    }

    // todo: replace that by a ResourceFinder?
    //       does not move often so maybe overkill but pattern being stable
    //       (org/apache/openjpa/*/localizer.properties) we could
    private void registerI18n(final Context context) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Stream.of(
                "org/apache/openjpa/abstractstore/localizer.properties",
                "org/apache/openjpa/ant/localizer.properties",
                "org/apache/openjpa/conf/localizer.properties",
                "org/apache/openjpa/datacache/localizer.properties",
                "org/apache/openjpa/ee/localizer.properties",
                "org/apache/openjpa/enhance/localizer.properties",
                "org/apache/openjpa/enhance/stats/localizer.properties",
                "org/apache/openjpa/event/kubernetes/localizer.properties",
                "org/apache/openjpa/event/localizer.properties",
                "org/apache/openjpa/instrumentation/jmx/localizer.properties",
                "org/apache/openjpa/jdbc/ant/localizer.properties",
                "org/apache/openjpa/jdbc/conf/localizer.properties",
                "org/apache/openjpa/jdbc/kernel/exps/localizer.properties",
                "org/apache/openjpa/jdbc/kernel/localizer.properties",
                "org/apache/openjpa/jdbc/meta/localizer.properties",
                "org/apache/openjpa/jdbc/meta/strats/localizer.properties",
                "org/apache/openjpa/jdbc/schema/localizer.properties",
                "org/apache/openjpa/jdbc/sql/localizer.properties",
                "org/apache/openjpa/kernel/exps/localizer.properties",
                "org/apache/openjpa/kernel/jpql/localizer.properties",
                "org/apache/openjpa/kernel/localizer.properties",
                "org/apache/openjpa/lib/ant/localizer.properties",
                "org/apache/openjpa/lib/conf/localizer.properties",
                "org/apache/openjpa/lib/graph/localizer.properties",
                "org/apache/openjpa/lib/jdbc/localizer.properties",
                "org/apache/openjpa/lib/log/localizer.properties",
                "org/apache/openjpa/lib/meta/localizer.properties",
                "org/apache/openjpa/lib/rop/localizer.properties",
                "org/apache/openjpa/lib/util/localizer.properties",
                "org/apache/openjpa/lib/xml/localizer.properties",
                "org/apache/openjpa/meta/localizer.properties",
                "org/apache/openjpa/persistence/criteria/localizer.properties",
                "org/apache/openjpa/persistence/jdbc/localizer.properties",
                "org/apache/openjpa/persistence/jest/localizer.properties",
                "org/apache/openjpa/persistence/localizer.properties",
                "org/apache/openjpa/persistence/meta/localizer.properties",
                "org/apache/openjpa/persistence/util/localizer.properties",
                "org/apache/openjpa/persistence/validation/localizer.properties",
                "org/apache/openjpa/slice/jdbc/localizer.properties",
                "org/apache/openjpa/slice/localizer.properties",
                "org/apache/openjpa/slice/transaction/localizer.properties",
                "org/apache/openjpa/util/localizer.properties"
        ).filter(it -> loader.getResource(it) != null).forEach(it -> {
            final ResourceBundleModel model = new ResourceBundleModel();
            model.setName(it.replace('/', '.').substring(0, it.length() - ".properties".length()));
            context.register(model);
        });
    }

    private void registerSPI(final Context context) {
        spiClasses(context)
                .distinct()
                .map(Class::getName)
                .forEach(it -> {
                    final ClassReflectionModel model = new ClassReflectionModel();
                    model.setName(it);
                    model.setAllPublicConstructors(true);
                    model.setAllDeclaredConstructors(true);
                    model.setAllPublicMethods(true);
                    context.register(model);
                });
    }

    // todo: cut more of that by a review of the reflection.arthur.json
    // one option is to precompute it for a pure openjpa deployment and just add all user impl only
    private Stream<? extends Class<?>> spiClasses(final Context context) {
        return Stream.of(
                "org.apache.openjpa.persistence.FetchPlan",
                "org.apache.openjpa.kernel.BrokerFactory",
                "org.apache.openjpa.lib.log.LogFactory",
                "org.apache.openjpa.lib.conf.Configurable",
                "org.apache.openjpa.util.CacheMap",
                "org.apache.openjpa.event.SingleJVMRemoteCommitProvider",
                "org.apache.openjpa.event.SingleJVMRemoteCommitProvider",
                "org.apache.openjpa.persistence.jdbc.PersistenceMappingFactory",
                "org.apache.openjpa.jdbc.kernel.TableJDBCSeq",
                "org.apache.openjpa.jdbc.kernel.ValueTableJDBCSeq",
                "org.apache.openjpa.jdbc.kernel.ClassTableJDBCSeq",
                "org.apache.openjpa.jdbc.kernel.NativeJDBCSeq",
                "org.apache.openjpa.kernel.TimeSeededSeq",
                "org.apache.openjpa.jdbc.kernel.TableJDBCSeq",
                "org.apache.openjpa.jdbc.kernel.ClassTableJDBCSeq",
                "org.apache.openjpa.kernel.TimeSeededSeq",
                "org.apache.openjpa.persistence.EntityManagerFactoryImpl",
                "org.apache.openjpa.jdbc.meta.MappingRepository",
                "org.apache.openjpa.meta.MetaDataRepository",
                "org.apache.openjpa.util.ClassResolverImpl",
                "org.apache.openjpa.datacache.DataCacheManagerImpl",
                "org.apache.openjpa.datacache.DefaultCacheDistributionPolicy",
                "org.apache.openjpa.datacache.ConcurrentDataCache",
                "org.apache.openjpa.datacache.ConcurrentQueryCache",
                "org.apache.openjpa.kernel.NoneLockManager",
                "org.apache.openjpa.kernel.VersionLockManager",
                "org.apache.openjpa.kernel.InverseManager",
                "org.apache.openjpa.kernel.InMemorySavepointManager",
                "org.apache.openjpa.event.LogOrphanedKeyAction",
                "org.apache.openjpa.event.ExceptionOrphanedKeyAction",
                "org.apache.openjpa.event.NoneOrphanedKeyAction",
                "org.apache.openjpa.ee.AutomaticManagedRuntime",
                "org.apache.openjpa.ee.JNDIManagedRuntime",
                "org.apache.openjpa.ee.InvocationManagedRuntime",
                "org.apache.openjpa.util.ProxyManagerImpl",
                "org.apache.openjpa.conf.DetachOptions$Loaded",
                "org.apache.openjpa.conf.DetachOptions$FetchGroups",
                "org.apache.openjpa.conf.DetachOptions$All",
                "org.apache.openjpa.conf.Compatibility",
                "org.apache.openjpa.conf.CallbackOptions",
                "org.apache.openjpa.event.LifecycleEventManager",
                "org.apache.openjpa.validation.ValidatingLifecycleEventManager",
                "org.apache.openjpa.instrumentation.InstrumentationManagerImpl",
                "org.apache.openjpa.audit.AuditLogger",
                "org.apache.openjpa.jdbc.sql.DBDictionary",
                "org.apache.openjpa.jdbc.kernel.AbstractUpdateManager",
                "org.apache.openjpa.jdbc.schema.DriverDataSource",
                "org.apache.openjpa.jdbc.schema.DynamicSchemaFactory",
                "org.apache.openjpa.jdbc.schema.LazySchemaFactory",
                "org.apache.openjpa.jdbc.schema.FileSchemaFactory",
                "org.apache.openjpa.jdbc.schema.TableSchemaFactory",
                "org.apache.openjpa.jdbc.sql.SQLFactoryImpl",
                "org.apache.openjpa.jdbc.meta.MappingDefaultsImpl",
                "org.apache.openjpa.jdbc.kernel.PreparedQueryCacheImpl",
                "org.apache.openjpa.jdbc.kernel.FinderCacheImpl",
                "org.apache.openjpa.jdbc.identifier.DBIdentifierUtilImpl",
                "org.apache.openjpa.lib.log.LogFactoryImpl",
                "org.apache.openjpa.lib.log.SLF4JLogFactory",
                "org.apache.openjpa.lib.log.NoneLogFactory",
                "org.apache.openjpa.slice.DistributionPolicy$Default",
                "org.apache.openjpa.slice.ReplicationPolicy$Default",
                "javax.persistence.spi.PersistenceProvider")
                .distinct()
                .map(it -> {
                    try {
                        return context.loadClass(it);
                    } catch (final IllegalStateException | NoClassDefFoundError ise) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(context::findHierarchy)
                .distinct()
                .flatMap(it -> Stream.concat(Stream.of(it), context.findImplementations(it).stream()))
                .distinct()
                .filter(it -> needsReflection(it.getName()))
                .flatMap(it -> {
                    if (it.getName().startsWith("org.apache.commons.logging.impl.")) {
                        try {
                            context.loadClass("org.apache.commons.logging.impl.LogFactoryImpl").getConstructor().newInstance();
                            return Stream.concat(Stream.of(it), addCommonsLogging(context));
                        } catch (final NoClassDefFoundError | Exception e) {
                            return Stream.empty();
                        }
                    }
                    return Stream.of(it);
                })
                .filter(it -> {
                    if ("org.apache.openjpa.jdbc.sql.PostgresDictionary".equals(it.getName())) {
                        try {
                            context.loadClass("org.postgresql.largeobject.LargeObjectManager");
                            return true;
                        } catch (final NoClassDefFoundError | Exception e) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    // todo: extract it in a commons-logging-knight and inherit from it automatically?
    private Stream<? extends Class<?>> addCommonsLogging(final Context context) {
        return Stream.of(
                "org.apache.commons.logging.LogFactory",
                "org.apache.commons.logging.impl.LogFactoryImpl",
                "org.apache.commons.logging.impl.Jdk14Logger")
                .map(n -> {
                    try {
                        return context.loadClass(n);
                    } catch (final NoClassDefFoundError | Exception ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    private boolean needsReflection(final String name) {
        return name.startsWith("org.apache.openjpa.") &&
                !name.equals("org.apache.openjpa.ee.OSGiManagedRuntime") &&
                !name.startsWith("org.apache.openjpa.ee.WAS") &&
                !name.contains("$1") &&
                !name.startsWith("org.apache.openjpa.lib.jdbc.LoggingConnectionDecorator$") &&
                !(name.endsWith("Comparator") && name.contains("$"));
    }

    private void registerJPAClasses(final Context context) {
        Stream.of(Entity.class, MappedSuperclass.class, Embeddable.class)
                .flatMap(it -> context.findAnnotatedClasses(it).stream())
                .flatMap(context::findHierarchy)
                .distinct()
                .flatMap(it -> {
                    final ClassReflectionModel entity = new ClassReflectionModel();
                    entity.setName(it.getName());
                    entity.setAllPublicConstructors(true);
                    entity.setAllPublicMethods(true);
                    entity.setAllDeclaredConstructors(true);
                    entity.setAllDeclaredFields(true);
                    entity.setAllDeclaredMethods(true);
                    return Stream.concat(Stream.of(entity), extractFieldTypesForReflection(it));
                })
                .distinct()
                .forEach(context::register);
    }

    private Stream<ClassReflectionModel> extractFieldTypesForReflection(final Class<?> entity) {
        try {
            final Field pcFieldTypes = entity.getDeclaredField("pcFieldTypes");
            pcFieldTypes.setAccessible(true);
            final Object types = pcFieldTypes.get(null);
            return Stream.of(Class[].class.cast(types))
                    .distinct() // todo: filter(it -> !it.isPrimitive())?
                    .map(type -> {
                        final ClassReflectionModel fieldType = new ClassReflectionModel();
                        fieldType.setName(type.getName());
                        return fieldType;
                    });
        } catch (final Exception e) {
            return Stream.empty();
        }
    }
}

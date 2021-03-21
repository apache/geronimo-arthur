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

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.geronimo.arthur.integrationtests.entities.Child;
import org.apache.geronimo.arthur.integrationtests.entities.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public final class OpenJPAMain {
    private OpenJPAMain() {
        // noop
    }

    /**
     * [main] INFO org.apache.geronimo.arthur.integrationtests.OpenJPAMain - findbyid => root:id=10000,name=root_1,children=[child:id=10001,name=child_2, child:id=10000,name=child_1]
     * [main] INFO org.apache.geronimo.arthur.integrationtests.OpenJPAMain - criteria builder => root:id=10000,name=root_1,children=[child:id=10001,name=child_2, child:id=10000,name=child_1]
     */
    public static void main(final String... args) throws SQLException {
        setIfMissing("hsqldb.reconfig_logging", "false");
        setIfMissing("org.slf4j.simpleLogger.logFile", "System.out");
        setIfMissing("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
        setIfMissing("org.slf4j.simpleLogger.log.org.apache.geronimo.arthur.integrationtests", "INFO");

        final BasicDataSource dataSource = createDataSource();
        final Map<String, Object> map = new HashMap<>();
        final Properties properties = new Properties();
        properties.setProperty("javax.persistence.schema-generation.database.action", "drop-and-create");
        properties.setProperty("openjpa.Log", "DefaultLevel=WARN, Runtime=WARN, Tool=WARN"); // SQL=TRACE for debugging purposes
        properties.setProperty("openjpa.Sequence", "class-table(Increment=20, InitialValue=1)");
        final EntityManagerFactory factory = ServiceLoader.load(PersistenceProvider.class).iterator().next()
                // use no xml option for now
                .createContainerEntityManagerFactory(newInfo(dataSource, properties), map);
        final Logger logger = LoggerFactory.getLogger(OpenJPAMain.class);
        try {
            final long rootId = createGraph(factory.createEntityManager());

            final EntityManager findByIdEm = factory.createEntityManager();
            logger.info("findbyid => " + findByIdEm.find(Root.class, rootId).toString());
            findByIdEm.close();

            final EntityManager criteriaBuilderEm = factory.createEntityManager();
            final CriteriaBuilder cb = criteriaBuilderEm.getCriteriaBuilder();
            final CriteriaQuery<Root> query = cb.createQuery(Root.class);
            final javax.persistence.criteria.Root<Root> from = query.from(Root.class);
            final CriteriaQuery<Root> criteriaQuery = query.select(from).where(cb.equal(from.get("id"), rootId));
            logger.info("criteria builder => " + criteriaBuilderEm.createQuery(criteriaQuery).getSingleResult());
            criteriaBuilderEm.close();
        } finally {
            factory.close();
            dataSource.close();
        }
        System.out.flush();
    }

    private static long createGraph(final EntityManager entityManager) {
        final EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();

        final Root root = new Root();
        root.setName("root_1");
        entityManager.persist(root);

        final Child child1 = new Child();
        child1.setName("child_1");
        child1.setRoot(root);
        entityManager.persist(child1);

        final Child child2 = new Child();
        child2.setName("child_2");
        child2.setRoot(root);
        entityManager.persist(child2);

        transaction.commit();
        entityManager.close();
        return root.getId();
    }

    private static BasicDataSource createDataSource() throws SQLException {
        // DriverManager.registerDriver(new jdbcDriver());
        final BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:mem:arthur;hsqldb.tx=MVCC");
        dataSource.setUsername("SA");
        dataSource.setPassword("");
        dataSource.setMinIdle(1);
        return dataSource;
    }

    private static PersistenceUnitInfo newInfo(final DataSource dataSource, final Properties properties) {
        return new PersistenceUnitInfo() {
            @Override
            public String getPersistenceUnitName() {
                return "arthur";
            }

            @Override
            public String getPersistenceProviderClassName() {
                return "org.apache.openjpa.persistence.PersistenceProviderImpl";
            }

            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }

            @Override
            public DataSource getJtaDataSource() {
                return dataSource;
            }

            @Override
            public DataSource getNonJtaDataSource() {
                return dataSource;
            }

            @Override
            public List<String> getMappingFileNames() {
                return emptyList();
            }

            @Override
            public List<URL> getJarFileUrls() {
                return emptyList();
            }

            @Override
            public URL getPersistenceUnitRootUrl() {
                return null;
            }

            @Override
            public List<String> getManagedClassNames() {
                return asList(Root.class.getName(), Child.class.getName());
            }

            @Override
            public boolean excludeUnlistedClasses() {
                return true;
            }

            @Override
            public SharedCacheMode getSharedCacheMode() {
                return SharedCacheMode.UNSPECIFIED;
            }

            @Override
            public ValidationMode getValidationMode() {
                return ValidationMode.AUTO;
            }

            @Override
            public Properties getProperties() {
                return properties;
            }

            @Override
            public String getPersistenceXMLSchemaVersion() {
                return "2.0";
            }

            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }

            @Override
            public void addTransformer(final ClassTransformer transformer) {
                // no-op
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return getClassLoader();
            }
        };
    }

    private static void setIfMissing(final String key, final String value) {
        System.setProperty(key, System.getProperty(key, value));
    }
}

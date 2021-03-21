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
package org.apache.geronimo.arthur.knight.derby;

import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Driver;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class DerbyExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        registerProperties(context);
        tryToRegisterSPI(context);
        context.findImplementations(Driver.class)
                .forEach(it -> registerClass(context, it.getName()));
        registerI18n(context);

        // extraDBMSclasses.properties + StoredFormatIds + ClassName + RegisteredFormatIds
        Stream.of(
                "org.apache.derby.catalog.GetProcedureColumns",
                "org.apache.derby.catalog.Java5SystemProcedures",
                "org.apache.derby.catalog.SystemProcedures",
                "org.apache.derby.catalog.TriggerNewTransitionRows",
                "org.apache.derby.catalog.TriggerOldTransitionRows",
                "org.apache.derby.catalog.UUID",
                "org.apache.derby.catalog.types.AggregateAliasInfo",
                "org.apache.derby.catalog.types.BitTypeIdImpl",
                "org.apache.derby.catalog.types.BooleanTypeIdImpl",
                "org.apache.derby.catalog.types.CharTypeIdImpl",
                "org.apache.derby.catalog.types.ClassAliasInfo",
                "org.apache.derby.catalog.types.DecimalTypeIdImpl",
                "org.apache.derby.catalog.types.DefaultInfoImpl",
                "org.apache.derby.catalog.types.DoubleTypeIdImpl",
                "org.apache.derby.catalog.types.IndexDescriptorImpl",
                "org.apache.derby.catalog.types.IntTypeIdImpl",
                "org.apache.derby.catalog.types.LongintTypeIdImpl",
                "org.apache.derby.catalog.types.LongvarbitTypeIdImpl",
                "org.apache.derby.catalog.types.LongvarcharTypeIdImpl",
                "org.apache.derby.catalog.types.MethodAliasInfo",
                "org.apache.derby.catalog.types.NationalCharTypeIdImpl",
                "org.apache.derby.catalog.types.NationalLongVarcharTypeIdImpl",
                "org.apache.derby.catalog.types.NationalVarcharTypeIdImpl",
                "org.apache.derby.catalog.types.RealTypeIdImpl",
                "org.apache.derby.catalog.types.RefTypeIdImpl",
                "org.apache.derby.catalog.types.ReferencedColumnsDescriptorImpl",
                "org.apache.derby.catalog.types.RoutineAliasInfo",
                "org.apache.derby.catalog.types.RowMultiSetImpl",
                "org.apache.derby.catalog.types.SmallintTypeIdImpl",
                "org.apache.derby.catalog.types.StatisticsImpl",
                "org.apache.derby.catalog.types.SynonymAliasInfo",
                "org.apache.derby.catalog.types.TinyintTypeIdImpl",
                "org.apache.derby.catalog.types.TypeDescriptorImpl",
                "org.apache.derby.catalog.types.TypesImplInstanceGetter",
                "org.apache.derby.catalog.types.UDTAliasInfo",
                "org.apache.derby.catalog.types.UserAggregateAliasInfo",
                "org.apache.derby.catalog.types.UserDefinedTypeIdImpl",
                "org.apache.derby.catalog.types.VarbitTypeIdImpl",
                "org.apache.derby.catalog.types.VarcharTypeIdImpl",
                "org.apache.derby.catalog.types.WorkUnitAliasInfo",
                "org.apache.derby.diag.ContainedRoles",
                "org.apache.derby.diag.ErrorLogReader",
                "org.apache.derby.diag.ErrorMessages",
                "org.apache.derby.diag.LockTable",
                "org.apache.derby.diag.SpaceTable",
                "org.apache.derby.diag.StatementCache",
                "org.apache.derby.diag.StatementDuration",
                "org.apache.derby.diag.TransactionTable",
                "org.apache.derby.iapi.db.ConsistencyChecker",
                "org.apache.derby.iapi.db.Factory",
                "org.apache.derby.iapi.db.OptimizerTrace",
                "org.apache.derby.iapi.db.PropertyInfo",
                "org.apache.derby.iapi.error.StandardException",
                "org.apache.derby.iapi.error.ThreadDump",
                "org.apache.derby.iapi.services.cache.ClassSizeCatalogImpl",
                "org.apache.derby.iapi.services.context.Context",
                "org.apache.derby.iapi.services.diag.DiagnosticUtil",
                "org.apache.derby.iapi.services.diag.DiagnosticableGeneric",
                "org.apache.derby.iapi.services.io.FormatableArrayHolder",
                "org.apache.derby.iapi.services.io.FormatableBitSet",
                "org.apache.derby.iapi.services.io.FormatableHashtable",
                "org.apache.derby.iapi.services.io.FormatableIntHolder",
                "org.apache.derby.iapi.services.io.FormatableLongHolder",
                "org.apache.derby.iapi.services.io.FormatableProperties",
                "org.apache.derby.iapi.services.io.Storable",
                "org.apache.derby.iapi.services.io.StoredFormatIds",
                "org.apache.derby.iapi.services.loader.GeneratedByteCode",
                "org.apache.derby.iapi.services.loader.GeneratedClass",
                "org.apache.derby.iapi.services.loader.GeneratedMethod",
                "org.apache.derby.iapi.sql.Activation",
                "org.apache.derby.iapi.sql.LanguageFactory",
                "org.apache.derby.iapi.sql.ParameterValueSet",
                "org.apache.derby.iapi.sql.ParameterValueSetFactory",
                "org.apache.derby.iapi.sql.ResultSet",
                "org.apache.derby.iapi.sql.Row",
                "org.apache.derby.iapi.sql.conn.Authorizer",
                "org.apache.derby.iapi.sql.conn.LanguageConnectionContext",
                "org.apache.derby.iapi.sql.dictionary.DataDictionary",
                "org.apache.derby.iapi.sql.dictionary.IndexRowGenerator",
                "org.apache.derby.iapi.sql.dictionary.TriggerDescriptor",
                "org.apache.derby.iapi.sql.execute.ConstantAction",
                "org.apache.derby.iapi.sql.execute.CursorResultSet",
                "org.apache.derby.iapi.sql.execute.ExecIndexRow",
                "org.apache.derby.iapi.sql.execute.ExecPreparedStatement",
                "org.apache.derby.iapi.sql.execute.ExecRow",
                "org.apache.derby.iapi.sql.execute.ExecRowBuilder",
                "org.apache.derby.iapi.sql.execute.ExecutionFactory",
                "org.apache.derby.iapi.sql.execute.NoPutResultSet",
                "org.apache.derby.iapi.sql.execute.ResultSetFactory",
                "org.apache.derby.iapi.sql.execute.RowFactory",
                "org.apache.derby.iapi.sql.execute.RunTimeStatistics",
                "org.apache.derby.iapi.store.access.Qualifier",
                "org.apache.derby.iapi.types.BitDataValue",
                "org.apache.derby.iapi.types.BitTypeId",
                "org.apache.derby.iapi.types.BooleanDataValue",
                "org.apache.derby.iapi.types.BooleanTypeId",
                "org.apache.derby.iapi.types.CharTypeId",
                "org.apache.derby.iapi.types.ConcatableDataValue",
                "org.apache.derby.iapi.types.DTSClassInfo",
                "org.apache.derby.iapi.types.DataTypeDescriptor",
                "org.apache.derby.iapi.types.DataValueDescriptor",
                "org.apache.derby.iapi.types.DataValueFactory",
                "org.apache.derby.iapi.types.DateTimeDataValue",
                "org.apache.derby.iapi.types.DateTypeId",
                "org.apache.derby.iapi.types.DecimalTypeId",
                "org.apache.derby.iapi.types.DoubleTypeId",
                "org.apache.derby.iapi.types.IntTypeId",
                "org.apache.derby.iapi.types.JSQLType",
                "org.apache.derby.iapi.types.LongintTypeId",
                "org.apache.derby.iapi.types.LongvarbitTypeId",
                "org.apache.derby.iapi.types.LongvarcharTypeId",
                "org.apache.derby.iapi.types.NationalCharTypeId",
                "org.apache.derby.iapi.types.NationalLongvarcharTypeId",
                "org.apache.derby.iapi.types.NationalVarcharTypeId",
                "org.apache.derby.iapi.types.NumberDataValue",
                "org.apache.derby.iapi.types.RealTypeId",
                "org.apache.derby.iapi.types.RefDataValue",
                "org.apache.derby.iapi.types.RefTypeId",
                "org.apache.derby.iapi.types.RowLocation",
                "org.apache.derby.iapi.types.SQLLongint",
                "org.apache.derby.iapi.types.SmallintTypeId",
                "org.apache.derby.iapi.types.StringDataValue",
                "org.apache.derby.iapi.types.TimeTypeId",
                "org.apache.derby.iapi.types.TimestampTypeId",
                "org.apache.derby.iapi.types.TinyintTypeId",
                "org.apache.derby.iapi.types.UserDataValue",
                "org.apache.derby.iapi.types.UserDefinedTypeId",
                "org.apache.derby.iapi.types.UserDefinedTypeIdV2",
                "org.apache.derby.iapi.types.UserDefinedTypeIdV3",
                "org.apache.derby.iapi.types.UserType",
                "org.apache.derby.iapi.types.VarbitTypeId",
                "org.apache.derby.iapi.types.VarcharTypeId",
                "org.apache.derby.iapi.types.VariableSizeDataValue",
                "org.apache.derby.iapi.types.XML (implementation of",
                "org.apache.derby.iapi.types.XMLDataValue",
                "org.apache.derby.iapi.types.XMLDataValue)",
                "org.apache.derby.impl.io.CPFile",
                "org.apache.derby.impl.io.DirStorageFactory",
                "org.apache.derby.impl.io.InputStreamFile",
                "org.apache.derby.impl.io.JarDBFile",
                "org.apache.derby.impl.io.URLFile",
                "org.apache.derby.impl.io.VFMemoryStorageFactory",
                "org.apache.derby.impl.jdbc.LOBStoredProcedure",
                "org.apache.derby.impl.jdbc.SQLExceptionFactory",
                "org.apache.derby.impl.jdbc.authentication.JNDIAuthenticationSchemeBase",
                "org.apache.derby.impl.jdbc.authentication.JNDIAuthenticationService",
                "org.apache.derby.impl.jdbc.authentication.LDAPAuthenticationSchemeImpl",
                "org.apache.derby.impl.services.monitor.BaseMonitor",
                "org.apache.derby.impl.services.monitor.FileMonitor",
                "org.apache.derby.impl.services.stream.RollingFileStream",
                "org.apache.derby.impl.services.stream.RollingFileStreamProvider",
                "org.apache.derby.impl.services.uuid.BasicUUID",
                "org.apache.derby.impl.services.uuid.BasicUUIDGetter",
                "org.apache.derby.impl.sql.CursorInfo",
                "org.apache.derby.impl.sql.CursorTableReference",
                "org.apache.derby.impl.sql.GenericColumnDescriptor",
                "org.apache.derby.impl.sql.GenericResultDescription",
                "org.apache.derby.impl.sql.GenericStorablePreparedStatement",
                "org.apache.derby.impl.sql.GenericTypeDescriptor",
                "org.apache.derby.impl.sql.GenericTypeId",
                "org.apache.derby.impl.sql.catalog.AliasDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.ColumnDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.ConglomerateDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.ConstraintDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.CoreDDFinderClassInfo",
                "org.apache.derby.impl.sql.catalog.DD_AristotleVersion",
                "org.apache.derby.impl.sql.catalog.DD_BuffyVersion",
                "org.apache.derby.impl.sql.catalog.DD_DB2J72",
                "org.apache.derby.impl.sql.catalog.DD_IvanovaVersion",
                "org.apache.derby.impl.sql.catalog.DD_MulanVersion",
                "org.apache.derby.impl.sql.catalog.DD_PlatoVersion",
                "org.apache.derby.impl.sql.catalog.DD_SocratesVersion",
                "org.apache.derby.impl.sql.catalog.DD_Version",
                "org.apache.derby.impl.sql.catalog.DD_XenaVersion",
                "org.apache.derby.impl.sql.catalog.DataDictionaryDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.DefaultDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.FileInfoFinder",
                "org.apache.derby.impl.sql.catalog.IndexRowGeneratorImpl",
                "org.apache.derby.impl.sql.catalog.OIDImpl",
                "org.apache.derby.impl.sql.catalog.ParameterDescriptorImpl",
                "org.apache.derby.impl.sql.catalog.RowListImpl",
                "org.apache.derby.impl.sql.catalog.SPSDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.SchemaDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.SequenceDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.TableDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.TriggerDescriptor",
                "org.apache.derby.impl.sql.catalog.TriggerDescriptorFinder",
                "org.apache.derby.impl.sql.catalog.ViewDescriptorFinder",
                "org.apache.derby.impl.sql.compile.MaxMinAggregateDefinition",
                "org.apache.derby.impl.sql.compile.OptTraceViewer",
                "org.apache.derby.impl.sql.compile.OptimizerTracer",
                "org.apache.derby.impl.sql.compile.SumAvgAggregateDefinition",
                "org.apache.derby.impl.sql.depend.DepClassInfo",
                "org.apache.derby.impl.sql.execute.AggregatorInfo",
                "org.apache.derby.impl.sql.execute.AggregatorInfoList",
                "org.apache.derby.impl.sql.execute.AvgAggregator",
                "org.apache.derby.impl.sql.execute.BaseActivation",
                "org.apache.derby.impl.sql.execute.BaseExpressionActivation",
                "org.apache.derby.impl.sql.execute.ColumnInfo",
                "org.apache.derby.impl.sql.execute.ConstantActionActivation",
                "org.apache.derby.impl.sql.execute.ConstraintInfo",
                "org.apache.derby.impl.sql.execute.CountAggregator",
                "org.apache.derby.impl.sql.execute.CurrentDatetime",
                "org.apache.derby.impl.sql.execute.CursorActivation",
                "org.apache.derby.impl.sql.execute.DeleteConstantAction",
                "org.apache.derby.impl.sql.execute.FKInfo",
                "org.apache.derby.impl.sql.execute.IndexColumnOrder",
                "org.apache.derby.impl.sql.execute.InsertConstantAction",
                "org.apache.derby.impl.sql.execute.MatchingClauseConstantAction",
                "org.apache.derby.impl.sql.execute.MaxMinAggregator",
                "org.apache.derby.impl.sql.execute.MergeConstantAction",
                "org.apache.derby.impl.sql.execute.OrderableAggregator",
                "org.apache.derby.impl.sql.execute.SavepointConstantAction",
                "org.apache.derby.impl.sql.execute.StdDevPAggregator",
                "org.apache.derby.impl.sql.execute.StdDevSAggregator",
                "org.apache.derby.impl.sql.execute.SumAggregator",
                "org.apache.derby.impl.sql.execute.TransactionConstantAction",
                "org.apache.derby.impl.sql.execute.TriggerInfo",
                "org.apache.derby.impl.sql.execute.UpdatableVTIConstantAction",
                "org.apache.derby.impl.sql.execute.UpdateConstantAction",
                "org.apache.derby.impl.sql.execute.UserDefinedAggregator",
                "org.apache.derby.impl.sql.execute.VarPAggregator",
                "org.apache.derby.impl.sql.execute.VarSAggregator",
                "org.apache.derby.impl.store.access.ConglomerateDirectory",
                "org.apache.derby.impl.store.access.PC_XenaVersion",
                "org.apache.derby.impl.store.access.PropertyConglomerate",
                "org.apache.derby.impl.store.access.StorableFormatId",
                "org.apache.derby.impl.store.access.btree.BranchControlRow",
                "org.apache.derby.impl.store.access.btree.LeafControlRow",
                "org.apache.derby.impl.store.access.btree.index.B2I",
                "org.apache.derby.impl.store.access.btree.index.B2IStaticCompiledInfo",
                "org.apache.derby.impl.store.access.btree.index.B2IUndo",
                "org.apache.derby.impl.store.access.btree.index.B2I_10_3",
                "org.apache.derby.impl.store.access.btree.index.B2I_v10_2",
                "org.apache.derby.impl.store.access.heap.Heap",
                "org.apache.derby.impl.store.access.heap.HeapClassInfo",
                "org.apache.derby.impl.store.access.heap.Heap_v10_2",
                "org.apache.derby.impl.store.raw.data.AllocPage",
                "org.apache.derby.impl.store.raw.data.AllocPageOperation",
                "org.apache.derby.impl.store.raw.data.ChainAllocPageOperation",
                "org.apache.derby.impl.store.raw.data.CompressSpacePageOperation",
                "org.apache.derby.impl.store.raw.data.CompressSpacePageOperation10_2",
                "org.apache.derby.impl.store.raw.data.ContainerOperation",
                "org.apache.derby.impl.store.raw.data.ContainerUndoOperation",
                "org.apache.derby.impl.store.raw.data.CopyRowsOperation",
                "org.apache.derby.impl.store.raw.data.DeleteOperation",
                "org.apache.derby.impl.store.raw.data.EncryptContainerOperation",
                "org.apache.derby.impl.store.raw.data.EncryptContainerUndoOperation",
                "org.apache.derby.impl.store.raw.data.FileContainer",
                "org.apache.derby.impl.store.raw.data.InitPageOperation",
                "org.apache.derby.impl.store.raw.data.InsertOperation",
                "org.apache.derby.impl.store.raw.data.InvalidatePageOperation",
                "org.apache.derby.impl.store.raw.data.LogicalUndoOperation",
                "org.apache.derby.impl.store.raw.data.PhysicalUndoOperation",
                "org.apache.derby.impl.store.raw.data.PurgeOperation",
                "org.apache.derby.impl.store.raw.data.RemoveFileOperation",
                "org.apache.derby.impl.store.raw.data.SetReservedSpaceOperation",
                "org.apache.derby.impl.store.raw.data.StoredPage",
                "org.apache.derby.impl.store.raw.data.StreamFileContainer",
                "org.apache.derby.impl.store.raw.data.UpdateFieldOperation",
                "org.apache.derby.impl.store.raw.data.UpdateOperation",
                "org.apache.derby.impl.store.raw.log.CheckpointOperation",
                "org.apache.derby.impl.store.raw.log.ChecksumOperation",
                "org.apache.derby.impl.store.raw.log.LogCounter",
                "org.apache.derby.impl.store.raw.log.LogRecord",
                "org.apache.derby.impl.store.raw.log.LogToFile",
                "org.apache.derby.impl.store.raw.xact.BeginXact",
                "org.apache.derby.impl.store.raw.xact.EndXact",
                "org.apache.derby.impl.store.raw.xact.GlobalXactId",
                "org.apache.derby.impl.store.raw.xact.TransactionTable",
                "org.apache.derby.impl.store.raw.xact.TransactionTableEntry",
                "org.apache.derby.impl.store.raw.xact.XAXactId",
                "org.apache.derby.impl.store.raw.xact.XactId",
                "org.apache.derby.jdbc.BasicEmbeddedConnectionPoolDataSource40",
                "org.apache.derby.jdbc.BasicEmbeddedDataSource40",
                "org.apache.derby.jdbc.BasicEmbeddedXADataSource40",
                "org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource",
                "org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40",
                "org.apache.derby.jdbc.EmbeddedDataSource",
                "org.apache.derby.jdbc.EmbeddedDataSource40",
                "org.apache.derby.jdbc.EmbeddedDriver",
                "org.apache.derby.jdbc.EmbeddedXADataSource",
                "org.apache.derby.jdbc.EmbeddedXADataSource40",
                "org.apache.derby.mbeans.Management",
                "org.apache.derby.osgi.EmbeddedActivator",
                "org.apache.derby.shared.common.sanity.ThreadDump",
                "org.apache.derby.tools.sysinfo",
                "org.apache.derby.vti.ForeignTableVTI",
                "org.apache.derby.vti.StringColumnVTI",
                "org.apache.derby.vti.UpdatableVTITemplate",
                "org.apache.derby.vti.VTICosting",
                "org.apache.derby.vti.VTIMetaDataTemplate",
                "org.apache.derby.vti.XmlVTI"
        ).distinct().forEach(it -> {
            try {
                registerClass(context, context.loadClass(it).getName());
            } catch (final IllegalStateException | NoClassDefFoundError ise) {
                // no-op
            }
        });
        context.register(new ClassReflectionModel("org.apache.derby.iapi.services.context.ContextManager", null, null, null, null, null, null, null, null, null, null));
        Stream.of(
                "org.apache.derby.jdbc.AutoloadedDriver",
                "org.apache.derby.jdbc.EmbeddedDriver"
        ).forEach(it -> {
            try {
                context.initializeAtRunTime(context.loadClass(it).getName());
            } catch (final IllegalStateException ise) {
                // no-op
            }
        });
    }

    private void registerI18n(final Context context) {
        IntStream.rangeClosed(0, 49).forEach(it -> context.register(new ResourceBundleModel("org.apache.derby.loc.m" + it)));
    }

    private void registerClass(final Context context, final String name) {
        final ClassReflectionModel model = new ClassReflectionModel();
        model.setName(name);
        model.setAllPublicConstructors(true);
        model.setAllDeclaredConstructors(true);
        context.register(model);
    }

    private void tryToRegisterSPI(final Context context) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ofNullable(loader.getResourceAsStream("org/apache/derby/modules.properties"))
                .map(res -> {
                    final Properties properties = new Properties();
                    try (final InputStream s = res) {
                        properties.load(s);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                    return properties;
                })
                .ifPresent(props -> props.stringPropertyNames().stream()
                        .map(props::getProperty)
                        .flatMap(it -> Stream.of(it.split(",")))
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .map(it -> {
                            try {
                                return context.loadClass(it);
                            } catch (final IllegalStateException ise) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .flatMap(context::findHierarchy)
                        .forEach(it -> {
                            final ClassReflectionModel model = new ClassReflectionModel();
                            model.setName(it.getName());
                            model.setAllDeclaredConstructors(true);
                            context.register(model);
                        }));
    }

    private void registerProperties(final Context context) {
        final ResourceModel resourceModel = new ResourceModel();
        resourceModel.setPattern("org\\/apache\\/derby\\/.+\\.properties");
        context.register(resourceModel);
    }
}

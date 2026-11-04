package com.his.infrastructure.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 迁移兼容只处理 V51 原索引，未知结构必须保留并中止迁移。 */
class V63OperationLogIndexCallbackTest {
    private final V63OperationLogIndexCallback callback = new V63OperationLogIndexCallback();
    private final Context context = mock(Context.class);
    private final MigrationInfo migration = mock(MigrationInfo.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement query = mock(PreparedStatement.class);
    private final ResultSet indexes = mock(ResultSet.class);
    private final Statement ddl = mock(Statement.class);

    @BeforeEach
    void setUp() throws SQLException {
        when(context.getMigrationInfo()).thenReturn(migration);
        when(migration.getScript()).thenReturn("V63__oplog_created_index.sql");
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(indexes);
        when(connection.createStatement()).thenReturn(ddl);
    }

    private void oldIndex() throws SQLException {
        when(indexes.next()).thenReturn(true, false);
        when(indexes.getString("COLUMN_NAME")).thenReturn("created_at");
        when(indexes.getInt("SEQ_IN_INDEX")).thenReturn(1);
        when(indexes.getInt("NON_UNIQUE")).thenReturn(1);
        when(indexes.getString("COLLATION")).thenReturn("A");
        when(indexes.getString("INDEX_TYPE")).thenReturn("BTREE");
        when(indexes.getString("IS_VISIBLE")).thenReturn("YES");
    }

    @Test
    void onlySupportsExactV63BeforeMigrationEvent() {
        assertTrue(callback.supports(Event.BEFORE_EACH_MIGRATE, context));
        assertFalse(callback.supports(Event.AFTER_EACH_MIGRATE, context));
        assertFalse(callback.supports(Event.BEFORE_MIGRATE, context));
        when(migration.getScript()).thenReturn("V51__oplog_created_index.sql");
        assertFalse(callback.supports(Event.BEFORE_EACH_MIGRATE, context));
        when(context.getMigrationInfo()).thenReturn(null);
        assertFalse(callback.supports(Event.BEFORE_EACH_MIGRATE, context));
        assertFalse(callback.canHandleInTransaction(Event.BEFORE_EACH_MIGRATE, context));
    }

    @Test
    void unrelatedEventDoesNotAccessDatabase() {
        callback.handle(Event.AFTER_EACH_MIGRATE, context);
        verifyNoInteractions(connection);
    }

    @Test
    void absentIndexDoesNotExecuteDdl() throws SQLException {
        when(indexes.next()).thenReturn(false);
        callback.handle(Event.BEFORE_EACH_MIGRATE, context);
        verify(connection, never()).createStatement();
    }

    @Test
    void removesOnlyOriginalV51Index() throws SQLException {
        oldIndex();
        callback.handle(Event.BEFORE_EACH_MIGRATE, context);
        verify(ddl).execute("ALTER TABLE sys_operation_log DROP INDEX idx_oplog_created");
        verify(query).close();
        verify(indexes).close();
        verify(ddl).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COLUMN_NAME", "COLLATION", "INDEX_TYPE", "IS_VISIBLE"})
    void rejectsUnexpectedIndexAttributes(String attribute) throws SQLException {
        oldIndex();
        when(indexes.getString(attribute)).thenReturn("未知结构");
        assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
        verify(connection, never()).createStatement();
    }

    @Test
    void refusesCompositeIndex() throws SQLException {
        oldIndex();
        when(indexes.next()).thenReturn(true, true, false);
        assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
        verify(connection, never()).createStatement();
    }

    @Test
    void refusesUniqueIndex() throws SQLException {
        oldIndex();
        when(indexes.getInt("NON_UNIQUE")).thenReturn(0);
        assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
        verify(connection, never()).createStatement();
    }

    @Test
    void refusesPrefixIndex() throws SQLException {
        oldIndex();
        when(indexes.getObject("SUB_PART")).thenReturn(10);
        assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
        verify(connection, never()).createStatement();
    }

    @Test
    void metadataFailureDoesNotDropIndex() throws SQLException {
        when(query.executeQuery()).thenThrow(new SQLException("元数据读取失败"));
        assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
        verify(connection, never()).createStatement();
    }
}

package com.his.infrastructure.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * V51 与 V63 使用同名日志索引，执行 V63 前处理 V51 的旧索引。
 * 保留已发布迁移脚本及其校验值；已经成功应用 V63 的数据库不会执行该回调。
 */
@Component
public class V63OperationLogIndexCallback implements Callback {
    private static final String SCRIPT = "V63__oplog_created_index.sql";

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE
                && context.getMigrationInfo() != null
                && SCRIPT.equals(context.getMigrationInfo().getScript());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        if (!supports(event, context)) {
            return;
        }
        String sql = "SELECT COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE, SUB_PART, COLLATION, INDEX_TYPE, IS_VISIBLE "
                + "FROM information_schema.statistics WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'sys_operation_log' AND INDEX_NAME = 'idx_oplog_created' "
                + "ORDER BY SEQ_IN_INDEX";
        try (PreparedStatement query = context.getConnection().prepareStatement(sql)) {
            try (ResultSet indexes = query.executeQuery()) {
                if (!indexes.next()) {
                    return;
                }
                boolean originalIndex = "created_at".equals(indexes.getString("COLUMN_NAME"))
                        && indexes.getInt("SEQ_IN_INDEX") == 1
                        && indexes.getInt("NON_UNIQUE") == 1
                        && indexes.getObject("SUB_PART") == null
                        && "A".equals(indexes.getString("COLLATION"))
                        && "BTREE".equals(indexes.getString("INDEX_TYPE"))
                        && "YES".equals(indexes.getString("IS_VISIBLE"));
                if (!originalIndex || indexes.next()) {
                    throw new FlywayException("V63 迁移中止：idx_oplog_created 不符合 V51 原始单列索引结构，"
                            + "已保留现有索引，请人工核对");
                }
            }
            // MySQL DDL 会隐式提交，允许在重试时索引已不存在；不自动修复 Flyway 失败历史。
            try (Statement ddl = context.getConnection().createStatement()) {
                ddl.execute("ALTER TABLE sys_operation_log DROP INDEX idx_oplog_created");
            }
        } catch (SQLException e) {
            throw new FlywayException("V63 日志索引兼容处理失败，迁移已中止", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "v63-operation-log-index-compatibility";
    }
}

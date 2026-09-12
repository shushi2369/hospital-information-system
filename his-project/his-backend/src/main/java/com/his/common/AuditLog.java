package com.his.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作留痕（指导文档 §7：关键操作可追溯）。方法执行后由 AuditLogAspect 写 sys_operation_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    /** 模块标识，如 system / basedata / billing */
    String module();

    /** 动作名称，如 收费 */
    String action();

    /** 业务类型，如 charge_bill */
    String bizType() default "";
}

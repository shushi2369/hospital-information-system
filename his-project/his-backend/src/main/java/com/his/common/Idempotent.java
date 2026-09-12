package com.his.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等提交（《01 总体架构设计》§6.4）。标注的接口必须携带 X-Idempotency-Key，
 * 由 IdempotentAspect 以 Redis SETNX 记录首响应，重复提交返回相同结果。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}

#!/bin/bash
# =====================================================================
# HIS 告警检查脚本（deploy/health_check.sh）
# 用法: 手动执行或注册计划任务（建议每 30 分钟）
# 检查: 后端存活 / 备份产物新鲜度 / 磁盘空间 / Flyway 失败迁移
# 输出: 控制台 + 告警日志（/c/his-runtime/logs/alert.log）
# =====================================================================
MYSQL="/c/his-runtime/mysql-8.0.36-winx64/bin/mysql.exe"
DB_USER="root"
DB_PASS="root123"
DB_NAME="his"
BACKUP_DIR="/c/his-runtime/backup"
LOG_DIR="/c/his-runtime/backend/logs"
ALERT_LOG="/c/his-runtime/logs/alert.log"

mkdir -p "$(dirname "$ALERT_LOG")"

alert() {
    local level="$1" msg="$2"
    local line="[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $msg"
    echo "$line"
    echo "$line" >> "$ALERT_LOG"
}

# 告警日志轮转：超过 5MB 保留最近 2000 行（探活每 30 分钟一写，无限增长会吃磁盘）
if [ -f "$ALERT_LOG" ] && [ "$(stat -c %s "$ALERT_LOG" 2>/dev/null || echo 0)" -gt 5242880 ]; then
    tail -2000 "$ALERT_LOG" > "$ALERT_LOG.tmp" && mv "$ALERT_LOG.tmp" "$ALERT_LOG"
fi

NOW_EPOCH=$(date +%s)
HAS_ALERT=0

# ---- 1. 后端存活（actuator 零凭据探活：真实登录会污染登录审计/撞限流，八十六轮审计）----
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 \
    "http://localhost:8080/actuator/health" 2>/dev/null)
[ -z "$HTTP_CODE" ] && HTTP_CODE="000"
if [ "$HTTP_CODE" != "200" ]; then
    alert "CRITICAL" "后端服务不可达 (HTTP $HTTP_CODE)"
    HAS_ALERT=1
fi

# ---- 1b. nginx 存活（Windows worker 崩溃后不会自愈，自动拉起）----
FE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "http://localhost/" 2>/dev/null || echo "000")
if [ "$FE_CODE" != "200" ]; then
    alert "CRITICAL" "nginx 不可达 (HTTP $FE_CODE)，尝试自动拉起"
    cd /c/his-runtime/nginx && start nginx 2>/dev/null
    sleep 3
    FE_CODE2=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "http://localhost/" 2>/dev/null || echo "000")
    if [ "$FE_CODE2" != "200" ]; then
        alert "CRITICAL" "nginx 自动拉起失败（HTTP $FE_CODE2），需人工介入"
        HAS_ALERT=1
    else
        alert "WARN" "nginx 已自动拉起恢复"
    fi
fi

# ---- 2. 备份产物新鲜度（>26 小时无新备份 = 异常）----
LATEST_BACKUP=$(ls -t "$BACKUP_DIR"/his_*.sql 2>/dev/null | head -1)
if [ -z "$LATEST_BACKUP" ]; then
    alert "CRITICAL" "备份目录无任何 .sql 产物"
    HAS_ALERT=1
else
    BACKUP_AGE=$(( (NOW_EPOCH - $(stat -c %Y "$LATEST_BACKUP" 2>/dev/null || stat -f %m "$LATEST_BACKUP" 2>/dev/null || echo 0)) / 3600 ))
    if [ "$BACKUP_AGE" -gt 26 ]; then
        alert "CRITICAL" "最新备份已 ${BACKUP_AGE} 小时（>26h 阈值）: $(basename $LATEST_BACKUP)"
        HAS_ALERT=1
    fi
fi

# ---- 3. 磁盘空间（C 盘使用 > 90%）----
DISK_PCT=$(df /c 2>/dev/null | tail -1 | awk '{print $5}' | tr -d '%')
if [ -n "$DISK_PCT" ] && [ "$DISK_PCT" -gt 90 ]; then
    alert "CRITICAL" "C 盘使用率 ${DISK_PCT}%（>90% 阈值）"
    HAS_ALERT=1
fi

# ---- 4. Flyway 失败迁移 ----
FAILED_MIGRATIONS=$($MYSQL -u$DB_USER -p$DB_PASS -D $DB_NAME -N -e \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;" 2>/dev/null || echo "0")
if [ "$FAILED_MIGRATIONS" -gt 0 ]; then
    alert "CRITICAL" "Flyway 有 ${FAILED_MIGRATIONS} 个失败迁移"
    HAS_ALERT=1
fi

# ---- 5. 后端日志近 30 分钟有无 ERROR ----
RECENT_ERRORS=$(grep -c "ERROR" "$LOG_DIR/his-backend.log" 2>/dev/null | tail -1)
if [ -n "$RECENT_ERRORS" ] && [ "$RECENT_ERRORS" -gt 0 ]; then
    # 只统计最近一段（简化：不按时间过滤，总量大只告警）
    ERROR_TAIL=$(tail -200 "$LOG_DIR/his-backend.log" 2>/dev/null | grep -c "ERROR" || echo 0)
    if [ "$ERROR_TAIL" -gt 5 ]; then
        alert "WARNING" "后端日志近 200 行有 ${ERROR_TAIL} 个 ERROR（可能异常）"
        HAS_ALERT=1
    fi
fi

# ---- 结果 ----
if [ "$HAS_ALERT" -eq 0 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [OK] 全部检查通过"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ALERT] 有 $HAS_ALERT 项告警"
fi

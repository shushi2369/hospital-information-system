#!/bin/bash
# =====================================================================
# HIS 一键部署脚本（deploy/deploy.sh）
# 用法: bash deploy/deploy.sh
# 功能: 停服 → 备份 → 替换 jar → 启动 → 健康检查（单条命令完成，减少人为失误）
# =====================================================================
set -e

BACKEND_JAR="his-backend/target/his-backend-0.1.0-SNAPSHOT.jar"
RUNTIME_DIR="/c/his-runtime/backend"
BACKUP_DIR="/c/his-runtime/backup/deployments"
FRONTEND_DIST="his-web/dist"
FRONTEND_DIR="/c/his-runtime/frontend"
LOG="/c/his-runtime/backend/logs/his-backend.log"
MYSQL="/c/his-runtime/mysql-8.0.36-winx64/bin/mysql.exe"
DB_USER="root"
DB_PASS="root123"
DB_NAME="his"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
echo "===== HIS 部署开始 $(date) ====="

# ---- 前置检查 ----
if [ ! -f "$BACKEND_JAR" ]; then
    echo "[ERROR] jar 不存在: $BACKEND_JAR（请先 mvn clean package）"
    exit 1
fi

# ---- Step 1: 停服 ----
echo "[Step 1/6] 停止后端服务..."
net stop HIS-Backend 2>/dev/null || echo "  (服务未运行，跳过)"
sleep 3

# ---- Step 2: 备份当前部署 ----
echo "[Step 2/6] 备份当前 jar..."
mkdir -p "$BACKUP_DIR"
if [ -f "$RUNTIME_DIR/app.jar" ]; then
    cp "$RUNTIME_DIR/app.jar" "$BACKUP_DIR/app_${TIMESTAMP}.jar"
    echo "  已备份到 $BACKUP_DIR/app_${TIMESTAMP}.jar"
fi
# 部署备份保留最近 10 份（几十 MB/次，无限累积会吃盘）
ls -t "$BACKUP_DIR"/app_*.jar 2>/dev/null | tail -n +11 | xargs -r rm -f

# ---- Step 3: 替换 jar ----
echo "[Step 3/6] 替换 jar..."
cp "$BACKEND_JAR" "$RUNTIME_DIR/app.jar"

# ---- Step 4: 启动 ----
echo "[Step 4/6] 启动后端服务..."
net start HIS-Backend
sleep 35

# ---- Step 5: 健康检查 ----
echo "[Step 5/6] 健康检查..."
for i in $(seq 1 6); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        "http://localhost:8080/actuator/health" 2>/dev/null)
    [ -z "$HTTP_CODE" ] && HTTP_CODE="000"
    if [ "$HTTP_CODE" = "200" ]; then
        echo "  API 正常 (200)"
        break
    fi
    echo "  等待服务就绪... ($i/6) code=$HTTP_CODE"
    sleep 10
done

if [ "$HTTP_CODE" != "200" ]; then
    echo "[ERROR] 服务未就绪！查看日志: tail -50 $LOG"
    echo "  回滚: cp $BACKUP_DIR/app_*.jar $RUNTIME_DIR/app.jar && net start HIS-Backend"
    exit 1
fi

# ---- Step 6: 检查 Flyway 迁移 ----
echo "[Step 6/6] 检查数据库迁移..."
FAILED=$($MYSQL -u$DB_USER -p$DB_PASS -D $DB_NAME -N -e \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;" 2>/dev/null || echo "ERR")
if [ "$FAILED" = "ERR" ]; then
    echo "  [WARN] 无法检查 flyway（可能 MySQL 未启动）"
elif [ "$FAILED" -gt 0 ]; then
    echo "  [ERROR] 有 $FAILED 个失败的迁移！查看: SELECT version FROM flyway_schema_history WHERE success=0;"
    echo "  回滚: 删除失败行 + DROP 残留表 + 重启"
else
    echo "  迁移全部成功"
fi

# ---- 前端部署 ----
if [ -d "$FRONTEND_DIST" ]; then
    echo "[前端] 部署 dist → $FRONTEND_DIR"
    cp -r "$FRONTEND_DIST"/* "$FRONTEND_DIR/"
    # 回收旧代构建产物（引用闭包外且 >3 天的哈希文件，详见脚本头注释）
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    python "$SCRIPT_DIR/cleanup_frontend_assets.py" --root "$FRONTEND_DIR" --apply \
        || echo "  [WARN] 资产回收失败（忽略，不影响部署）"
fi

echo "===== HIS 部署完成 $(date) ====="
echo "系统入口: http://localhost/"

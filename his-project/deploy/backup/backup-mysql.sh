#!/usr/bin/env bash
# HIS MySQL 每日全量备份（指导文档 §7：每日自动备份）
# 建议 crontab: 0 2 * * * /opt/his/deploy/backup/backup-mysql.sh
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/his/backup}"
KEEP_DAYS="${KEEP_DAYS:-7}"
CONTAINER="${CONTAINER:-his-mysql}"
DB_NAME="${DB_NAME:-his}"
DB_PASSWORD="${DB_PASSWORD:?请设置 DB_PASSWORD 环境变量}"

mkdir -p "${BACKUP_DIR}"
STAMP=$(date +%Y%m%d_%H%M%S)
TARGET="${BACKUP_DIR}/${DB_NAME}_${STAMP}.sql.gz"

# pipefail：dump 中途失败时不留截断产物（截断 .sql.gz 会被"新鲜度检查"误判为最新备份正常）
if ! docker exec "${CONTAINER}" sh -c "exec mysqldump --single-transaction --routines --triggers -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" | gzip > "${TARGET}"; then
    rm -f "${TARGET}"
    echo "[$(date '+%F %T')] 备份失败，已删除截断产物" >&2
    exit 1
fi
echo "[$(date '+%F %T')] 备份完成: ${TARGET} ($(du -h "${TARGET}" | cut -f1))"

# 保留最近 N 天
find "${BACKUP_DIR}" -name "${DB_NAME}_*.sql.gz" -mtime +"${KEEP_DAYS}" -delete

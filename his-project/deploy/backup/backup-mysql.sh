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

docker exec "${CONTAINER}" sh -c "exec mysqldump --single-transaction --routines --triggers -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" | gzip > "${TARGET}"
echo "[$(date '+%F %T')] 备份完成: ${TARGET} ($(du -h "${TARGET}" | cut -f1))"

# 保留最近 N 天
find "${BACKUP_DIR}" -name "${DB_NAME}_*.sql.gz" -mtime +"${KEEP_DAYS}" -delete

rem HIS daily backup: local 7-day retention + off-site sync to D:\his-backup (same-disk backup is NOT disaster recovery)
@echo off
set BACKUP_DIR=C:\his-runtimeackup
set OFFSITE_DIR=D:\his-backup
if not exist %BACKUP_DIR% mkdir %BACKUP_DIR%
if not exist %OFFSITE_DIR% mkdir %OFFSITE_DIR%
set STAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%
C:\his-runtime\mysql-8.0.36-winx64in\mysqldump -h127.0.0.1 -uroot -proot123 --single-transaction --routines --triggers his > %BACKUP_DIR%\his_%STAMP%.sql
forfiles /p %BACKUP_DIR% /m his_*.sql /d -7 /c "cmd /c del @path" 2>nul
robocopy %BACKUP_DIR% %OFFSITE_DIR% his_*.sql /NFL /NDL /NJH /NJS >nul

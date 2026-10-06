@echo off
rem HIS daily backup: local 7-day retention + off-site sync to D:\his-backup
rem Round 79: original had 0x08 backspace bytes - completely rewritten ASCII-only
setlocal enabledelayedexpansion

set BACKUP_DIR=C:\his-runtime\backup
set OFFSITE_DIR=D:\his-backup
set MYSQL_BIN=C:\his-runtime\mysql-8.0.36-winx64\bin
set DB_USER=root
set DB_PASS=root123
set DB_NAME=his
set LOG_FILE=%BACKUP_DIR%\backup.log

for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value 2^>nul') do set "ldt=%%a"
set TS=%ldt:~0,4%%ldt:~4,2%%ldt:~6,2%_%ldt:~8,2%%ldt:~10,2%%ldt:~12,2%

echo %date% %time% backup start >> "%LOG_FILE%"

"%MYSQL_BIN%\mysqldump" -u%DB_USER% -p%DB_PASS% --single-transaction --routines %DB_NAME% > "%BACKUP_DIR%\his_%TS%.sql" 2>>"%LOG_FILE%"
if errorlevel 1 (
    echo %date% %time% BACKUP FAILED >> "%LOG_FILE%"
    exit /b 1
)

for %%f in ("%BACKUP_DIR%\his_%TS%.sql") do set SIZE=%%~zf
if !SIZE! LSS 1048576 (
    echo %date% %time% BACKUP TOO SMALL: !SIZE! bytes >> "%LOG_FILE%"
    exit /b 1
)
findstr /c:"Dump completed" "%BACKUP_DIR%\his_%TS%.sql" >nul 2>&1
if errorlevel 1 (
    echo %date% %time% DUMP INCOMPLETE >> "%LOG_FILE%"
    exit /b 1
)

echo %date% %time% backup OK: his_%TS%.sql !SIZE! bytes >> "%LOG_FILE%"

forfiles /p "%BACKUP_DIR%" /m "his_*.sql" /d -7 /c "cmd /c del @path" 2>nul
robocopy "%BACKUP_DIR%" "%OFFSITE_DIR%" "his_*.sql" /mov /minage:1 >nul 2>&1
rem 九十轮生命周期审计：/e 镜像会连 deployments jar 一起无限同步（D 盘只增不减）——
rem 异盘只保留 dump；且 D 盘必须自轮转（本地 /mov 后仅存当日，轮转责任全在异盘侧）
robocopy "%OFFSITE_DIR%" "%OFFSITE_DIR%" /mov /minage:30 >nul 2>&1

echo %date% %time% backup complete >> "%LOG_FILE%"
endlocal

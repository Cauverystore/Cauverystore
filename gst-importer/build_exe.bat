@echo off
REM Builds gst_master_importer.exe (one-file) with PyInstaller.
REM Copies the official HSN/SAC and CBIC rate seed into data/ so the exe is
REM self-contained, then packages.
REM
REM Usage:  build_exe.bat
REM Output: dist\gst_master_importer.exe

setlocal
cd /d "%~dp0"

set "REPO_MASTER=..\backend-java\src\main\resources\master-data"

if not exist "%REPO_MASTER%\hsn_master.json" (
    echo ERROR: could not find %REPO_MASTER%\hsn_master.json
    echo Run this from the gst-importer folder of the Cauvery Store repo.
    exit /b 1
)

if not exist "data" mkdir data
copy /y "%REPO_MASTER%\hsn_master.json" "data\hsn_master.json" >nul
copy /y "%REPO_MASTER%\gst_rate_seed.json" "data\gst_rate_seed.json" >nul

echo Installing build dependencies...
python -m pip install --quiet pyinstaller requests pandas "psycopg2-binary>=2.9.10" || exit /b 1

echo Building gst_master_importer.exe ...
python -m PyInstaller --noconfirm --clean gst_master_importer.spec || exit /b 1

echo.
echo Done. Executable: dist\gst_master_importer.exe
echo Test it with:  dist\gst_master_importer.exe --help
endlocal

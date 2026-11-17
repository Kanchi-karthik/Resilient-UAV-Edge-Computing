@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo    GitHub One-Click Upload: Resilient UAV Edge Computing (M.Tech Phase 1)
echo    Target: https://github.com/Kanchi-karthik/Resilient-UAV-Edge-Computing.git
echo ==============================================================================
echo.

set GIT_EXE=D:\MTech_CSE\Research_Tag\tools\mingit\cmd\git.exe

if not exist "%GIT_EXE%" (
    where git >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set GIT_EXE=git
    ) else (
        echo [ERROR] Git was not found!
        pause
        exit /b 1
    )
)

echo Current Repository Status:
"%GIT_EXE%" status -s
echo.

echo ------------------------------------------------------------------------------
echo GitHub requires authentication to push to:
echo   https://github.com/Kanchi-karthik/Resilient-UAV-Edge-Computing
echo.
echo If you have a GitHub Personal Access Token (Classic token with 'repo' scope):
echo (Generate one in 30 seconds at: https://github.com/settings/tokens )
echo ------------------------------------------------------------------------------
echo.
set /p GH_TOKEN="Enter your GitHub Personal Access Token (or press Enter to try standard login): "

if "%GH_TOKEN%"=="" (
    echo.
    echo Attempting push via standard authentication...
    "%GIT_EXE%" push -u origin main
) else (
    echo.
    echo Pushing with provided authentication token...
    "%GIT_EXE%" push https://Kanchi-karthik:%GH_TOKEN%@github.com/Kanchi-karthik/Resilient-UAV-Edge-Computing.git main
)

if %ERRORLEVEL% equ 0 (
    echo.
    echo ==============================================================================
    echo [SUCCESS] Your repository is now live on GitHub!
    echo URL: https://github.com/Kanchi-karthik/Resilient-UAV-Edge-Computing
    echo ==============================================================================
) else (
    echo.
    echo [NOTE] If authentication failed, please verify:
    echo 1. Your token has the 'repo' permission checked at https://github.com/settings/tokens
    echo 2. Your token was pasted correctly without spaces.
)

echo.
pause

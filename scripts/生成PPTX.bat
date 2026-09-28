@echo off
chcp 65001 >nul
echo ========================================
echo   开题答辩 PPTX 生成器
echo   音乐资源推荐与歌单管理系统
echo ========================================
echo.
echo 正在启动 PowerPoint 并生成 PPTX...
echo （第一次运行会弹出 PowerPoint 窗口，属正常现象）
echo.

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "gen_ppt.ps1"

if exist "..\defense\答辩PPT.pptx" (
    echo.
    echo ========================================
    echo   ✅ PPTX 生成成功！
    echo ========================================
    echo.
    start "" "..\defense\答辩PPT.pptx"
) else (
    echo.
    echo   ❌ 生成失败，请看上方错误信息
    echo.
    pause
)

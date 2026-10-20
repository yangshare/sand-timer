#Requires -Version 5.1
# SandTimer 一键发布脚本
# 用法：双击 scripts\release.bat，或命令行 .\scripts\release.ps1 1.2.0
# 流程：校验工作区 -> 自动升级 android 版本号 -> 提交 -> 打 tag -> 推送
#       GitHub Actions 随后自动构建 APK 并发布到 GitHub Releases

param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

# ---- 定位仓库根目录（scripts/ 的上一级）----
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "===================================="
Write-Host " SandTimer 一键发布"
Write-Host "===================================="
Write-Host ""

# ---- 版本号 ----
if (-not $Version) {
    $Version = Read-Host "请输入版本号（例如 1.2.0）"
}
$Version = $Version.Trim().TrimStart('v')
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "错误：版本号格式应为 x.y.z（例如 1.2.0），收到：$Version" -ForegroundColor Red
    exit 1
}
$tag = "v$Version"

# ---- 工作区必须干净（发布内容 = 当前提交）----
$dirty = git status --porcelain
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：git status 执行失败，请确认当前目录是仓库" -ForegroundColor Red
    exit 1
}
if ($dirty) {
    Write-Host "错误：工作区有未提交的改动，请先提交或暂存（stash）：" -ForegroundColor Red
    $dirty | ForEach-Object { Write-Host "  $_" }
    exit 1
}

# ---- tag 不能已存在 ----
if (git tag --list $tag) {
    Write-Host "错误：tag $tag 已存在，请换一个版本号" -ForegroundColor Red
    exit 1
}

$branch = git branch --show-current
$commit = git log -1 --format="%h %s"
Write-Host "即将发布 $tag，基于分支 $branch 当前提交："
Write-Host "  $commit"
Write-Host ""

# ---- 升级 android/app/build.gradle.kts 中的版本号 ----
$gradleFile = Join-Path $repoRoot "android\app\build.gradle.kts"
# 用 .NET 读写，避免 Windows PowerShell 对无 BOM UTF-8 的编码误判
$content = [System.IO.File]::ReadAllText($gradleFile)
$codeMatch = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')
if (-not $codeMatch.Success) {
    Write-Host "错误：未在 build.gradle.kts 中找到 versionCode" -ForegroundColor Red
    exit 1
}
$newCode = [int]$codeMatch.Groups[1].Value + 1
$content = [regex]::Replace($content, 'versionCode\s*=\s*\d+', "versionCode = $newCode")
$content = [regex]::Replace($content, 'versionName\s*=\s*"[^"]*"', ('versionName = "' + $Version + '"'))
[System.IO.File]::WriteAllText($gradleFile, $content)

Write-Host "已升级版本：versionName = $Version，versionCode = $newCode"

# ---- 提交、打 tag、推送 ----
git add $gradleFile
if ($LASTEXITCODE -ne 0) { Write-Host "错误：git add 失败" -ForegroundColor Red; exit 1 }

git commit -m "chore(release): $tag"
if ($LASTEXITCODE -ne 0) { Write-Host "错误：git commit 失败" -ForegroundColor Red; exit 1 }

git tag $tag
if ($LASTEXITCODE -ne 0) { Write-Host "错误：创建 tag 失败" -ForegroundColor Red; exit 1 }
Write-Host ""
Write-Host "tag $tag 已创建，推送到 GitHub ..."

git push origin $branch
if ($LASTEXITCODE -ne 0) { Write-Host "错误：推送分支失败" -ForegroundColor Red; exit 1 }

git push origin $tag
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：推送 tag 失败（tag 只打在了本地，可手动 git push origin $tag 重试）" -ForegroundColor Red
    exit 1
}

# ---- 完成，给出后续链接 ----
$remoteUrl = git remote get-url origin
$repoPath = ($remoteUrl -replace '\.git$', '') -replace '^(https://|git@)github\.com[:/]', 'https://github.com/'
Write-Host ""
Write-Host "===================================="
Write-Host " 发布完成！$tag 已推送" -ForegroundColor Green
Write-Host " GitHub Actions 正在自动构建 APK："
Write-Host "   $repoPath/actions"
Write-Host " 构建成功后 Release 会出现在："
Write-Host "   $repoPath/releases"
Write-Host " 其他人直接在 Releases 页下载 SandTimer-$Version.apk 安装即可"
Write-Host "===================================="

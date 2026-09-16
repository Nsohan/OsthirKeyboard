<#
.SYNOPSIS
    OsthirKeyboard 1-Click Release Automation Script for PowerShell / Antigravity

.DESCRIPTION
    Automates the entire release lifecycle:
    1. Detects or accepts versionName and versionCode from build.gradle.kts.
    2. Builds and signs release APK via Gradle (assembleRelease).
    3. Packages the release APK into OsthirKeyboard.apk (required by in-app UpdateChecker)
       and OsthirKeyboard-v<version>.apk.
    4. Computes file sizes and SHA-256 checksums.
    5. Reads or generates release notes from RELEASE_NOTES_v<version>.md, Fastlane changelog, or git history.
    6. Creates git tag v<version> and pushes commits & tags to GitHub.
    7. Automatically creates the GitHub Release via GitHub REST API (using Git Credential Manager token or GITHUB_TOKEN)
       and uploads the release APK assets with progress indicators.
    8. Fallback to browser release draft if no token is available.

.PARAMETER Version
    Optional override for the version name (e.g. "0.0.03"). If omitted, reads from build.gradle.kts.

.PARAMETER NotesFile
    Optional path to markdown release notes file.

.PARAMETER Notes
    Optional raw text for release notes.

.PARAMETER Token
    Optional GitHub Personal Access Token. If omitted, queries Git Credential Manager or $env:GITHUB_TOKEN.

.PARAMETER Draft
    Create the GitHub Release as a draft instead of publishing immediately.

.PARAMETER PreRelease
    Mark the GitHub Release as a pre-release.

.PARAMETER SkipBuild
    Skip running Gradle assembleRelease and use already-built APK.

.PARAMETER SkipPush
    Skip git push and GitHub release creation (local build and tag only).

.PARAMETER DryRun
    Simulate execution without modifying git or creating releases on GitHub.

.EXAMPLE
    .\release.ps1
    Builds, tags, pushes, and creates release on GitHub automatically!

.EXAMPLE
    .\release.ps1 -DryRun
    Preview everything that would happen without modifying anything.

.EXAMPLE
    .\release.ps1 -SkipBuild
    Publish release without rebuilding the APK.
#>

[CmdletBinding()]
param(
    [string]$Version,
    [string]$NotesFile,
    [string]$Notes,
    [string]$Token,
    [switch]$Draft,
    [switch]$PreRelease,
    [switch]$AutoCommit,
    [switch]$SkipBuild,
    [switch]$SkipPush,
    [switch]$DryRun
)

$ErrorActionPreference = "Continue"

# --- Output helpers ---
function Write-Step { param([string]$msg) Write-Host "`n[+] $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn { param([string]$msg) Write-Host "[!] $msg" -ForegroundColor Yellow }
function Write-Err { param([string]$msg) Write-Host "[X] $msg" -ForegroundColor Red }
function Write-Info { param([string]$msg) Write-Host "    $msg" -ForegroundColor Gray }

Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "       OsthirKeyboard Automated Release Pipeline          " -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta

# --- 1. Environment Setup (Java & ADB) ---
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdkCandidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-21"
    )
    foreach ($path in $jdkCandidates) {
        if (Test-Path "$path\bin\java.exe") {
            $env:JAVA_HOME = $path
            $env:Path = "$path\bin;$env:Path"
            Write-Info "Set JAVA_HOME to: $path"
            break
        }
    }
}

# --- 2. Verify Working Directory & Git Repository ---
if (-not (Test-Path "build.gradle.kts")) {
    Write-Err "build.gradle.kts not found! Please run this script from the repository root."
    exit 1
}

$gradleContent = Get-Content "build.gradle.kts" -Raw
$parsedVersionName = $null
$parsedVersionCode = $null

if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $parsedVersionName = $matches[1]
}
if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
    $parsedVersionCode = $matches[1]
}

$releaseVersion = if ($Version) { $Version } else { $parsedVersionName }
$releaseCode = $parsedVersionCode

if (-not $releaseVersion) {
    Write-Err "Could not determine versionName from build.gradle.kts and no -Version argument was supplied."
    exit 1
}

$tagName = "v$releaseVersion"
$releaseTitle = "OsthirKeyboard v$releaseVersion 🚀"

Write-Step "Target Version: $releaseVersion (versionCode: $releaseCode) | Tag: $tagName"

# --- 3. Check Remote Repository Info ---
$remoteUrl = git remote get-url origin 2>$null
$repoOwner = "Nsohan"
$repoName = "OsthirKeyboard"

if ($remoteUrl -and ($remoteUrl -match 'github\.com[:/]([^/]+)/([^/\.]+)')) {
    $repoOwner = $matches[1]
    $repoName = $matches[2]
}
Write-Info "Target GitHub Repo: $repoOwner/$repoName"

# --- 4. Handle Pending Git Changes ---
$gitStatus = git status -s
if ($gitStatus) {
    Write-Warn "Working tree has uncommitted modifications / untracked files:"
    $gitStatus | ForEach-Object { Write-Info "  $_" }
    
    if (-not $DryRun -and -not $SkipPush) {
        $shouldCommit = $AutoCommit
        if (-not $shouldCommit -and [Environment]::UserInteractive) {
            $confirm = Read-Host "Stage and commit these changes before releasing? (y/N)"
            $shouldCommit = ($confirm -eq 'y' -or $confirm -eq 'Y')
        }
        
        if ($shouldCommit) {
            git add .
            git commit -m "chore: release $tagName"
            Write-Success "Committed working tree changes."
        } else {
            Write-Warn "Continuing with uncommitted changes. (Only committed changes will be included in the release tag)"
        }
    }
}

# --- 5. Build Release APK via Gradle ---
$apkReleaseDir = "build\outputs\apk\release"
$rawApkPath = "$apkReleaseDir\OsthirKeyboard-release.apk"

if (-not $SkipBuild) {
    Write-Step "Building release APK with Gradle: .\gradlew assembleRelease"
    if ($DryRun) {
        Write-Info "[DryRun] Would execute: .\gradlew assembleRelease"
    } else {
        if (-not (Test-Path "release.keystore")) {
            Write-Warn "release.keystore was not found in root directory! The build may fail or be unsigned."
        }
        
        $buildStartTime = Get-Date
        .\gradlew assembleRelease
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Gradle build failed with exit code $LASTEXITCODE."
            exit $LASTEXITCODE
        }
        $buildDuration = (Get-Date) - $buildStartTime
        Write-Success "Release APK built successfully in $($buildDuration.TotalSeconds.ToString('F1'))s."
    }
} else {
    Write-Step "Skipping Gradle build as requested (-SkipBuild)."
}

# --- 6. Verify and Package APK Assets ---
if (-not $DryRun) {
    if (-not (Test-Path $rawApkPath)) {
        Write-Err "Could not find release APK at $rawApkPath!"
        exit 1
    }

    # OsthirKeyboard.apk is REQUIRED by UpdateChecker (EXPECTED_APK_ASSET_NAME = "OsthirKeyboard.apk")
    $standardApkPath = "$apkReleaseDir\OsthirKeyboard.apk"
    # OsthirKeyboard-vX.X.XX.apk is convenient for users downloading manually from GitHub
    $versionedApkPath = "$apkReleaseDir\OsthirKeyboard-$tagName.apk"

    Copy-Item $rawApkPath $standardApkPath -Force
    Copy-Item $rawApkPath $versionedApkPath -Force

    $apkHash = (Get-FileHash -Path $standardApkPath -Algorithm SHA256).Hash
    $apkSizeMB = ((Get-Item $standardApkPath).Length / 1MB).ToString("0.00")

    Write-Success "Packaged APK Assets in ${apkReleaseDir}:"
    Write-Info "  - OsthirKeyboard.apk ($apkSizeMB MB)"
    Write-Info "  - OsthirKeyboard-$tagName.apk ($apkSizeMB MB)"
    Write-Info "  - SHA-256: $apkHash"
} else {
    $apkHash = "DRYRUN_MOCK_SHA256_HASH"
    $apkSizeMB = "8.16"
}

# --- 7. Resolve Release Notes ---
$releaseNotes = $null

if ($Notes) {
    $releaseNotes = $Notes
} elseif ($NotesFile -and (Test-Path $NotesFile)) {
    $releaseNotes = Get-Content $NotesFile -Raw
    Write-Info "Loaded release notes from $NotesFile"
} elseif (Test-Path "RELEASE_NOTES_$tagName.md") {
    $releaseNotes = Get-Content "RELEASE_NOTES_$tagName.md" -Raw
    Write-Info "Loaded release notes from RELEASE_NOTES_$tagName.md"
} elseif (Test-Path "RELEASE_NOTES.md") {
    $releaseNotes = Get-Content "RELEASE_NOTES.md" -Raw
    Write-Info "Loaded release notes from RELEASE_NOTES.md"
} elseif ($releaseCode -and (Test-Path "fastlane\metadata\android\en-US\changelogs\$releaseCode.txt")) {
    $releaseNotes = Get-Content "fastlane\metadata\android\en-US\changelogs\$releaseCode.txt" -Raw
    Write-Info "Loaded release notes from Fastlane changelog ($releaseCode.txt)"
} else {
    # Auto-generate from git commits since previous tag
    $previousTag = git describe --tags --abbrev=0 2>$null
    if ($previousTag) {
        $commits = git log "$previousTag..HEAD" --pretty=format:"* %s (%h)"
        $releaseNotes = "# OsthirKeyboard $tagName`n`n### Changes since ${previousTag}:`n$commits"
    } else {
        $commits = git log -n 10 --pretty=format:"* %s (%h)"
        $releaseNotes = "# OsthirKeyboard $tagName`n`n### Recent changes:`n$commits"
    }
    Write-Info "Auto-generated release notes from git log."
}

# --- 8. Git Tagging and Pushing ---
Write-Step "Git Tagging and Push"

$existingTag = git tag -l $tagName
if (-not $existingTag) {
    if ($DryRun) {
        Write-Info "[DryRun] Would create git tag: $tagName"
    } else {
        git tag -a $tagName -m "Release $tagName"
        Write-Success "Created git tag: $tagName"
    }
} else {
    Write-Info "Tag $tagName already exists locally."
}

if (-not $SkipPush) {
    if ($DryRun) {
        Write-Info "[DryRun] Would execute: git push origin (current branch) --tags"
    } else {
        $currentBranch = (git branch --show-current).Trim()
        if (-not $currentBranch) { $currentBranch = "main" }
        Write-Info "Pushing commits to origin/$currentBranch..."
        git push origin $currentBranch
        Write-Info "Pushing tag $tagName to origin..."
        git push origin $tagName
        if ($LASTEXITCODE -ne 0) {
            Write-Info "Updating tag ref on remote (--force)..."
            git push origin $tagName --force
        }
        Write-Success "Pushed git commits and tag $tagName to origin."
    }
} else {
    Write-Info "Skipping git push (-SkipPush)."
}

# --- 9. GitHub Release Creation via API ---
if (-not $SkipPush) {
    Write-Step "Publishing GitHub Release"

    # Acquire GitHub Token
    $githubToken = $Token
    if (-not $githubToken -and $env:GITHUB_TOKEN) { $githubToken = $env:GITHUB_TOKEN }
    if (-not $githubToken -and $env:GH_TOKEN) { $githubToken = $env:GH_TOKEN }

    if (-not $githubToken) {
        # Query Windows Git Credential Manager
        try {
            $inputData = "protocol=https`nhost=github.com`n"
            $credOutput = $inputData | git credential fill 2>$null
            foreach ($line in ($credOutput -split "`r?`n")) {
                if ($line -match '^password=(.+)$') {
                    $githubToken = $matches[1]
                    break
                }
            }
        } catch {}
    }

    if ($githubToken) {
        Write-Info "Authenticated with GitHub API as repository owner."

        if ($DryRun) {
            Write-Info "[DryRun] Would call GitHub API to create release $tagName and upload APK assets."
        } else {
            $releaseApiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases"
            $tagApiUrl = "$releaseApiUrl/tags/$tagName"

            # 1. Check if release already exists
            $existingRelease = $null
            $getReleaseJson = & curl.exe -s -H "Authorization: Bearer $githubToken" -H "Accept: application/vnd.github+json" "$tagApiUrl"
            if ($getReleaseJson) {
                try {
                    $parsed = $getReleaseJson | ConvertFrom-Json
                    if ($parsed.id) {
                        $existingRelease = $parsed
                        Write-Info "Found existing release for $tagName (ID: $($existingRelease.id))."
                    }
                } catch {}
            }

            $targetReleaseId = $null
            $htmlUrl = $null

            if ($null -eq $existingRelease) {
                # Create Release via UTF-8 JSON payload to avoid Windows ANSI/codepage emoji mangling
                Write-Info "Creating new GitHub Release: $releaseTitle..."
                $releasePayload = @{
                    tag_name         = $tagName
                    target_commitish = if ($currentBranch) { $currentBranch } else { "main" }
                    name             = $releaseTitle
                    body             = $releaseNotes
                    draft            = [bool]$Draft
                    prerelease       = [bool]$PreRelease
                } | ConvertTo-Json -Depth 10

                $tmpJsonFile = [System.IO.Path]::GetTempFileName()
                $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
                [System.IO.File]::WriteAllBytes($tmpJsonFile, $utf8NoBom.GetBytes($releasePayload))

                $createResp = & curl.exe -s -S -X POST `
                    -H "Authorization: Bearer $githubToken" `
                    -H "Accept: application/vnd.github+json" `
                    -H "Content-Type: application/json" `
                    --data-binary "@$tmpJsonFile" `
                    "$releaseApiUrl"

                Remove-Item $tmpJsonFile -Force -ErrorAction SilentlyContinue

                $newRelease = $null
                try { $newRelease = $createResp | ConvertFrom-Json } catch {}

                if (-not $newRelease -or -not $newRelease.id) {
                    Write-Err "Failed to create GitHub release. Response: $createResp"
                    exit 1
                }

                $targetReleaseId = $newRelease.id
                $htmlUrl = $newRelease.html_url
                Write-Success "Created GitHub release (ID: $targetReleaseId)!"
            } else {
                $targetReleaseId = $existingRelease.id
                $htmlUrl = $existingRelease.html_url
            }

            # 2. Upload APK Assets
            $assetsToUpload = @(
                @{ Name = "OsthirKeyboard.apk"; Path = "$apkReleaseDir\OsthirKeyboard.apk" },
                @{ Name = "OsthirKeyboard-$tagName.apk"; Path = "$apkReleaseDir\OsthirKeyboard-$tagName.apk" }
            )

            # Check existing assets to avoid duplicates or replace old ones
            $assetsResp = & curl.exe -s -H "Authorization: Bearer $githubToken" -H "Accept: application/vnd.github+json" "$releaseApiUrl/$targetReleaseId/assets"
            $existingAssets = @()
            try { $existingAssets = $assetsResp | ConvertFrom-Json } catch {}

            foreach ($asset in $assetsToUpload) {
                $assetName = $asset.Name
                $assetPath = $asset.Path

                # Delete duplicate asset if it exists
                $match = $existingAssets | Where-Object { $_.name -eq $assetName }
                if ($match) {
                    Write-Info "Replacing existing asset '$assetName' (ID: $($match.id))..."
                    & curl.exe -s -X DELETE `
                        -H "Authorization: Bearer $githubToken" `
                        -H "Accept: application/vnd.github+json" `
                        "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($match.id)" | Out-Null
                }

                Write-Info "Uploading $assetName..."
                $uploadUri = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$targetReleaseId/assets?name=$assetName"

                # Use curl.exe for robust large binary streaming and progress meter
                $curlResult = & curl.exe --progress-bar -f -s -S -X POST `
                    -H "Authorization: Bearer $githubToken" `
                    -H "Content-Type: application/vnd.android.package-archive" `
                    --data-binary "@$assetPath" `
                    "$uploadUri" 2>&1

                if ($LASTEXITCODE -ne 0) {
                    Write-Warn "Upload of $assetName exited with code ${LASTEXITCODE}: $curlResult"
                } else {
                    Write-Success "Uploaded $assetName successfully."
                }
            }

            Write-Host "`n"
            Write-Host "==========================================================" -ForegroundColor Green
            Write-Host "         🎉 RELEASE PUBLISHED SUCCESSFULLY!               " -ForegroundColor Green
            Write-Host "==========================================================" -ForegroundColor Green
            Write-Host "Release URL: " -NoNewline; Write-Host "$htmlUrl" -ForegroundColor Cyan
            Write-Host "Tag:         " -NoNewline; Write-Host "$tagName" -ForegroundColor Yellow
            Write-Host "Direct APK:  " -NoNewline; Write-Host "https://github.com/$repoOwner/$repoName/releases/download/$tagName/OsthirKeyboard.apk" -ForegroundColor White
            Write-Host "SHA-256:     " -NoNewline; Write-Host "$apkHash" -ForegroundColor DarkGray
            Write-Host "==========================================================" -ForegroundColor Green
        }
    } else {
        # Fallback if no GitHub token can be resolved
        Write-Warn "No GitHub Personal Access Token or Git Credential Manager token found."
        Write-Info "Copied release notes to clipboard!"
        try { Set-Clipboard -Value $releaseNotes } catch {}

        $escapedTitle = [System.Uri]::EscapeDataString($releaseTitle)
        $draftUrl = "https://github.com/$repoOwner/$repoName/releases/new?tag=$tagName&title=$escapedTitle"
        Write-Info "Opening release creation page in your browser..."
        Start-Process $draftUrl
        Start-Process (Resolve-Path $apkReleaseDir)
        Write-Success "Please drag-and-drop OsthirKeyboard.apk into the browser release page and paste the release notes (Ctrl+V)."
    }
}

Write-Step "All Release Steps Completed!"

<#
.SYNOPSIS
    Interactive Git Version Control Menu for PopLauncher (Local Only, No Remote / GitHub)
.DESCRIPTION
    Provides an interactive terminal UI to view status, diffs, log history,
    stage files, create commits, manage branches, and launch visual tools (gitk / git-gui / tig).
#>

[Console]::ResetColor()

function Show-Header {
    Clear-Host
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "          POPLAUNCHER - LOCAL GIT VERSION CONTROL          " -ForegroundColor Yellow -BackgroundColor Black
    Write-Host "                  (Local Only - No GitHub)                " -ForegroundColor DarkGray
    Write-Host "==========================================================" -ForegroundColor Cyan
    $branch = git rev-parse --abbrev-ref HEAD 2>$null
    $lastCommit = git log -1 --oneline 2>$null
    Write-Host " Current Branch : " -NoNewline; Write-Host "$branch" -ForegroundColor Green
    Write-Host " Last Commit    : " -NoNewline; Write-Host "$lastCommit" -ForegroundColor Gray
    Write-Host " Remote Target  : " -NoNewline; Write-Host "None (Local Air-Gapped Repository)" -ForegroundColor Magenta
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Pause-Menu {
    Write-Host ""
    Write-Host "Press any key to return to menu..." -ForegroundColor DarkYellow
    [void][System.Console]::ReadKey($true)
}

while ($true) {
    Show-Header
    Write-Host " 1. " -NoNewline -ForegroundColor Yellow; Write-Host "Show Status (git status)"
    Write-Host " 2. " -NoNewline -ForegroundColor Yellow; Write-Host "View Uncommitted Changes (git diff)"
    Write-Host " 3. " -NoNewline -ForegroundColor Yellow; Write-Host "View Staged Changes (git diff --staged)"
    Write-Host " 4. " -NoNewline -ForegroundColor Yellow; Write-Host "Commit History Graph (git log --graph --oneline)"
    Write-Host " 5. " -NoNewline -ForegroundColor Yellow; Write-Host "Stage All Changes (git add .)"
    Write-Host " 6. " -NoNewline -ForegroundColor Yellow; Write-Host "Create a Commit (git commit)"
    Write-Host " 7. " -NoNewline -ForegroundColor Yellow; Write-Host "Create / Switch Branch"
    Write-Host " 8. " -NoNewline -ForegroundColor Yellow; Write-Host "Discard Local Changes (git restore)"
    Write-Host " 9. " -NoNewline -ForegroundColor Yellow; Write-Host "Launch Graphical Git Visualizer (gitk)"
    Write-Host "10. " -NoNewline -ForegroundColor Yellow; Write-Host "Launch Graphical Commit Tool (git-gui)"
    Write-Host "11. " -NoNewline -ForegroundColor Yellow; Write-Host "Launch Terminal Text Git Browser (tig)"
    Write-Host " Q. " -NoNewline -ForegroundColor Red;    Write-Host "Exit"
    Write-Host ""
    $choice = Read-Host "Select an option [1-11, Q]"

    switch ($choice.Trim().ToUpper()) {
        "1" {
            Clear-Host
            Write-Host "--- Git Status ---" -ForegroundColor Cyan
            git status
            Pause-Menu
        }
        "2" {
            Clear-Host
            Write-Host "--- Working Tree Diffs (Unstaged) ---" -ForegroundColor Cyan
            git diff
            Pause-Menu
        }
        "3" {
            Clear-Host
            Write-Host "--- Staged Diffs ---" -ForegroundColor Cyan
            git diff --staged
            Pause-Menu
        }
        "4" {
            Clear-Host
            Write-Host "--- Commit History Graph ---" -ForegroundColor Cyan
            git log --graph --decorate --all --oneline -n 25
            Pause-Menu
        }
        "5" {
            Clear-Host
            Write-Host "Staging all changes..." -ForegroundColor Cyan
            git add .
            git status -s
            Write-Host "All changes staged successfully." -ForegroundColor Green
            Pause-Menu
        }
        "6" {
            Clear-Host
            Write-Host "--- Create Commit ---" -ForegroundColor Cyan
            $status = git status -s
            if (-not $status) {
                Write-Host "No changes detected to commit." -ForegroundColor Yellow
            } else {
                git status -s
                Write-Host ""
                $stageAll = Read-Host "Stage all pending changes before committing? (y/n)"
                if ($stageAll -eq "y" -or $stageAll -eq "Y") {
                    git add .
                }
                $msg = Read-Host "Enter commit message"
                if ([string]::IsNullOrWhiteSpace($msg)) {
                    Write-Host "Commit aborted: message cannot be empty." -ForegroundColor Red
                } else {
                    git commit -m "$msg"
                    Write-Host "Commit created successfully!" -ForegroundColor Green
                }
            }
            Pause-Menu
        }
        "7" {
            Clear-Host
            Write-Host "--- Branch Management ---" -ForegroundColor Cyan
            git branch -a
            Write-Host ""
            Write-Host "1. Create new branch"
            Write-Host "2. Switch branch"
            Write-Host "3. Back"
            $bChoice = Read-Host "Selection"
            if ($bChoice -eq "1") {
                $bName = Read-Host "Enter new branch name"
                if ($bName) {
                    git checkout -b $bName
                    Write-Host "Switched to new branch: $bName" -ForegroundColor Green
                }
            } elseif ($bChoice -eq "2") {
                $bName = Read-Host "Enter branch name to switch to"
                if ($bName) {
                    git checkout $bName
                }
            }
            Pause-Menu
        }
        "8" {
            Clear-Host
            Write-Host "--- Discard Local Uncommitted Changes ---" -ForegroundColor Red
            Write-Host "WARNING: This will revert unstaged file modifications!" -ForegroundColor Yellow
            $confirm = Read-Host "Are you sure? Type 'discard' to confirm"
            if ($confirm -eq "discard") {
                git restore .
                Write-Host "Unstaged changes discarded." -ForegroundColor Green
            } else {
                Write-Host "Cancelled." -ForegroundColor Gray
            }
            Pause-Menu
        }
        "9" {
            Write-Host "Opening gitk visual history browser..." -ForegroundColor Cyan
            Start-Process "gitk"
        }
        "10" {
            Write-Host "Opening git-gui visual commit tool..." -ForegroundColor Cyan
            Start-Process "git-gui"
        }
        "11" {
            Write-Host "Opening tig terminal interface..." -ForegroundColor Cyan
            & tig
        }
        "Q" {
            Write-Host "Goodbye!" -ForegroundColor Green
            break
        }
        default {
            Write-Host "Invalid option selected." -ForegroundColor Red
            Start-Sleep -Seconds 1
        }
    }
}

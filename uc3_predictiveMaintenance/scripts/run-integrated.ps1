$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
Write-Host "Starting integrated DT app on http://localhost:8080"
gradle bootRun --args='--spring.profiles.active=integrated'

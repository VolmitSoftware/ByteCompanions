param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$EvidenceDirectory = '.qa/dependency-integrity'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidenceRoot = [IO.Path]::GetFullPath($(if ([IO.Path]::IsPathRooted($EvidenceDirectory)) { $EvidenceDirectory } else { Join-Path $projectRoot $EvidenceDirectory }))
$copyRoot = Join-Path $evidenceRoot ('mutation-m2-' + [Guid]::NewGuid().ToString('N'))
$sourceRoot = Join-Path $env:USERPROFILE '.m2/repository/org/spigotmc'
$relativeJar = 'spigot/26.1.2-R0.1-SNAPSHOT/spigot-26.1.2-R0.1-SNAPSHOT.jar'
$sourceJar = Join-Path $sourceRoot $relativeJar
$originalHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle/dependency-integrity.json') -Raw | ConvertFrom-Json
$identity = 'org.spigotmc:spigot:26.1.2-R0.1-SNAPSHOT|spigot-26.1.2-R0.1-SNAPSHOT.jar'
if ($manifest.artifacts.$identity -cne $originalHash) { throw 'Source Spigot artifact does not match the reviewed manifest' }
$destinationParent = Join-Path $copyRoot 'org'
New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
Copy-Item -LiteralPath $sourceRoot -Destination $destinationParent -Recurse
$copyJar = [IO.Path]::GetFullPath((Join-Path $copyRoot ('org/spigotmc/' + $relativeJar)))
if (-not $copyJar.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Mutation target escaped the evidence directory' }
$copyHash = (Get-FileHash -LiteralPath $copyJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($copyHash -cne $originalHash) { throw 'Copied artifact did not match its source' }
$stream = [IO.File]::Open($copyJar, [IO.FileMode]::Append, [IO.FileAccess]::Write)
try { $stream.WriteByte(0) } finally { $stream.Dispose() }
$mutatedHash = (Get-FileHash -LiteralPath $copyJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($mutatedHash -ceq $originalHash) { throw 'Mutation did not alter artifact bytes' }
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$startedUtc = [DateTimeOffset]::UtcNow.ToString('o')
Push-Location $projectRoot
try {
    $mutationLog = Join-Path $evidenceRoot 'dependency-mutation.log'
    & ./gradlew.bat ':adapter-spigot26_1:compileJava' "-Dmaven.repo.local=$copyRoot" '--no-configuration-cache' '--console=plain' *> $mutationLog
    $mutationExit = $LASTEXITCODE
    $mutationText = Get-Content -LiteralPath $mutationLog -Raw
    $expectedFailure = "Dependency integrity mismatch: ${identity}; expected $originalHash, actual $mutatedHash"
    if ($mutationExit -eq 0 -or -not $mutationText.Contains($expectedFailure)) { throw "Isolated artifact mutation was not rejected by the expected checksum check; see $mutationLog" }
    $cacheLog = Join-Path $evidenceRoot 'configuration-cache-rejection.log'
    & ./gradlew.bat 'help' '--configuration-cache' '--console=plain' *> $cacheLog
    $cacheExit = $LASTEXITCODE
    $cacheText = Get-Content -LiteralPath $cacheLog -Raw
    if ($cacheExit -eq 0 -or -not $cacheText.Contains('Dependency integrity verification requires configuration cache to be disabled')) { throw "Explicit configuration cache was not rejected; see $cacheLog" }
} finally {
    Pop-Location
    $finalSourceHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $resolvedCopyRoot = [IO.Path]::GetFullPath($copyRoot)
    if (-not $resolvedCopyRoot.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolvedCopyRoot -Leaf) -notmatch '^mutation-m2-[a-f0-9]{32}$') { throw 'Cleanup target escaped its generated evidence directory' }
    Remove-Item -LiteralPath $resolvedCopyRoot -Recurse -Force
    if ($finalSourceHash -cne $originalHash) { throw 'Original Maven artifact changed during the negative check' }
}
[ordered]@{
    Passed = $true
    StartedUtc = $startedUtc
    CompletedUtc = [DateTimeOffset]::UtcNow.ToString('o')
    Identity = $identity
    OriginalPath = $sourceJar
    MutatedCopyPath = $copyJar
    OriginalSha256 = $originalHash
    FinalOriginalSha256 = $finalSourceHash
    MutatedSha256 = $mutatedHash
    MutatedCopyRemoved = -not (Test-Path -LiteralPath $copyRoot)
    MutationExitCode = $mutationExit
    ConfigurationCacheExitCode = $cacheExit
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'dependency-negative-checks.json') -Encoding utf8

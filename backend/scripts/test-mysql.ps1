[CmdletBinding()]
param(
    [string]$MySqlBin,
    [string]$Maven = 'mvn.cmd'
)

$ErrorActionPreference = 'Stop'
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $MySqlBin) { $MySqlBin = Split-Path -Parent (Get-Command mysqld.exe -ErrorAction Stop).Source }
$serverExe = Join-Path $MySqlBin 'mysqld.exe'
$clientExe = Join-Path $MySqlBin 'mysql.exe'
$adminExe = Join-Path $MySqlBin 'mysqladmin.exe'
foreach ($binary in @($serverExe, $clientExe, $adminExe)) {
    if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "Missing MySQL binary: $binary" }
}
$mavenExe = (Get-Command $Maven -ErrorAction Stop).Source
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
if ($port -in @(3306, 33060)) { throw 'Refusing a normal MySQL service port; run again.' }
$runRoot = Join-Path ([IO.Path]::GetTempPath()) ('whut-acm-mysql-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runRoot | Out-Null
$dataDir = Join-Path $runRoot 'data'
New-Item -ItemType Directory -Path $dataDir | Out-Null
$schema = 'whut_acm_test_' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
# Database-level GRANT treats underscores as wildcards, even inside quoted identifiers.
$grantTarget = '`' + $schema.Replace('_', '\_') + '`'
$rootPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$testPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$savedEnvironment = @{}
foreach ($name in @('MYSQL_PWD','MYSQL_TEST_URL','MYSQL_TEST_USERNAME','MYSQL_TEST_PASSWORD','SPRING_PROFILES_ACTIVE')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$serverProcess = $null
$verified = $false
$succeeded = $false
$mysqlExit = 1
$resultDir = Join-Path $backendRoot 'target'
New-Item -ItemType Directory -Path $resultDir -Force | Out-Null
$testLog = Join-Path $resultDir 'mysql-regression.log'

function Invoke-SetupSql([string]$Sql) {
    # SQL and generated credentials travel via stdin/environment, never command-line password arguments.
    $output = $Sql | & $clientExe --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root --batch --skip-column-names 2>&1
    if ($LASTEXITCODE -ne 0) {
        $diagnostic = (($output | Out-String).Trim()).Replace($rootPassword, '[redacted]').Replace($testPassword, '[redacted]')
        throw "Temporary MySQL setup query failed: $diagnostic"
    }
    return $output
}

try {
    Write-Host "[mysql-test] Initializing disposable data directory: $runRoot"
    $init = Start-Process -FilePath $serverExe -WindowStyle Hidden -ArgumentList @('--no-defaults','--initialize-insecure',"--datadir=`"$dataDir`"") -Wait -PassThru `
        -RedirectStandardOutput (Join-Path $runRoot 'initialize.out.log') -RedirectStandardError (Join-Path $runRoot 'initialize.error.log')
    if ($init.ExitCode -ne 0) { throw "MySQL initialization failed; diagnostics: $runRoot" }
    Write-Host "[mysql-test] Starting loopback-only instance on port $port (existing services are untouched)."
    $serverProcess = Start-Process -FilePath $serverExe -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults','--console',"--datadir=`"$dataDir`"",'--bind-address=127.0.0.1',"--port=$port",'--mysqlx=0',
        '--skip-log-bin','--local-infile=0','--innodb-buffer-pool-size=128M','--max-connections=40','--default-time-zone=+08:00'
    ) -RedirectStandardOutput (Join-Path $runRoot 'server.out.log') -RedirectStandardError (Join-Path $runRoot 'server.error.log')
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($serverProcess.HasExited) { throw "Temporary MySQL exited; diagnostics: $runRoot" }
        & $adminExe --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root ping *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Temporary MySQL did not become ready; diagnostics: $runRoot" }
    # Read-only identity check before any CREATE/ALTER/GRANT; refuse a port claimed by another instance.
    $actualData = [string](Invoke-SetupSql 'SELECT @@datadir;')
    $expectedPath = [IO.Path]::GetFullPath($dataDir).TrimEnd('\','/')
    $actualPath = [IO.Path]::GetFullPath($actualData.Trim().Replace('/', '\')).TrimEnd('\','/')
    if (-not [string]::Equals($expectedPath, $actualPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Connected MySQL instance does not own the disposable directory; refusing to write.'
    }
    $verified = $true
    Invoke-SetupSql "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';" | Out-Null
    $env:MYSQL_PWD = $rootPassword
    Invoke-SetupSql @"
CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'whut_test'@'127.0.0.1' IDENTIFIED BY '$testPassword';
CREATE USER 'whut_test'@'localhost' IDENTIFIED BY '$testPassword';
GRANT ALL PRIVILEGES ON $grantTarget.* TO 'whut_test'@'127.0.0.1';
GRANT ALL PRIVILEGES ON $grantTarget.* TO 'whut_test'@'localhost';
"@ | Out-Null
    $version = Invoke-SetupSql 'SELECT VERSION();'
    Write-Host "[mysql-test] MySQL $version; restricted account; schema $schema."
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
    $env:MYSQL_TEST_URL = "jdbc:mysql://127.0.0.1:$port/$schema`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    $env:MYSQL_TEST_USERNAME = 'whut_test'
    $env:MYSQL_TEST_PASSWORD = $testPassword
    $env:SPRING_PROFILES_ACTIVE = 'mysql-test'
    $testStartedAt = [DateTime]::UtcNow
    Push-Location $backendRoot
    try { & $mavenExe --batch-mode verify *> $testLog; $mysqlExit = $LASTEXITCODE }
    finally { Pop-Location }
    if ($mysqlExit -ne 0) { throw "MySQL regression failed (exit $mysqlExit). See $testLog; database retained at $runRoot" }
    $surefire = Join-Path $resultDir 'surefire-reports'
    $smokeFile = Join-Path $surefire 'TEST-com.whut.training.repository.RealMySqlSmokeTest.xml'
    if (-not (Test-Path -LiteralPath $smokeFile)) { throw 'MySQL identity test report is missing; refusing to report success.' }
    $smoke = [xml](Get-Content -LiteralPath $smokeFile -Raw)
    if ((Get-Item -LiteralPath $smokeFile).LastWriteTimeUtc -lt $testStartedAt.AddSeconds(-2) `
            -or [int]$smoke.testsuite.tests -ne 1 -or [int]$smoke.testsuite.skipped -ne 0 `
            -or [int]$smoke.testsuite.errors -ne 0 -or [int]$smoke.testsuite.failures -ne 0) {
        throw 'A fresh, successful MySQL identity test is required; tests may have been skipped.'
    }
    $snapshot = Join-Path $resultDir 'mysql-regression-reports'
    New-Item -ItemType Directory -Path $snapshot -Force | Out-Null
    Get-ChildItem -LiteralPath $surefire -Filter 'TEST-*.xml' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $snapshot $_.Name)
    }
    $succeeded = $true
    Write-Host "[mysql-test] Maven verify passed. Report: $testLog"
} finally {
    if ($serverProcess -and -not $serverProcess.HasExited) {
        if ($verified) {
            $env:MYSQL_PWD = $rootPassword
            & $adminExe --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root shutdown *> $null
            $serverProcess.WaitForExit(10000) | Out-Null
        }
        if (-not $serverProcess.HasExited) { $serverProcess.Kill(); $serverProcess.WaitForExit(10000) | Out-Null }
    }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    if ($succeeded -and (Test-Path -LiteralPath $dataDir)) {
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        $resolvedData = (Resolve-Path -LiteralPath $dataDir).Path
        if ((Split-Path -Leaf $resolvedRun) -notmatch '^whut-acm-mysql-[a-f0-9]{32}$' `
                -or -not [string]::Equals((Split-Path -Parent $resolvedData), $resolvedRun, [StringComparison]::OrdinalIgnoreCase) `
                -or (Split-Path -Leaf $resolvedData) -ne 'data' `
                -or ((Get-Item -LiteralPath $resolvedData).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Refusing cleanup: unexpected disposable data path.'
        }
        Remove-Item -LiteralPath $resolvedData -Recurse -Force
        Write-Host '[mysql-test] Temporary instance stopped and generated database files removed; logs retained.'
    } else { Write-Host "[mysql-test] Temporary instance stopped; diagnostic directory retained: $runRoot" }
}

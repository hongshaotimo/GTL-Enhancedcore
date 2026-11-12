$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskJdk = Get-ChildItem -Path (Join-Path $env:ProgramFiles 'Eclipse Adoptium') -Directory -Filter 'jdk-21*' | Select-Object -First 1
$taskGson = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1') -Recurse -Filter '*.jar' | Select-Object -First 1
$taskClasses = Join-Path $taskRoot '_work/pink-retire-20261110/classes'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
$taskClasspath = "$taskClasses;$(Join-Path $taskRoot 'build/classes/java/main');$(Join-Path $taskRoot 'src/main/resources');$($taskGson.FullName)"
Push-Location $taskRoot
try {
    & (Join-Path $taskJdk.FullName 'bin/javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses `
        src/main/java/com/gtl/enhancedcore/common/world/plot/CampusPresetRegistry.java `
        src/main/java/com/gtl/enhancedcore/common/world/plot/PlotSettings.java `
        src/audit/java/com/gtl/enhancedcore/audit/PinkDreamRetirementRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Pink retirement regression compilation failed.' }
    & (Join-Path $taskJdk.FullName 'bin/java.exe') -cp $taskClasspath com.gtl.enhancedcore.audit.PinkDreamRetirementRegression
    if ($LASTEXITCODE -ne 0) { throw 'Pink retirement regression failed.' }
} finally {
    Pop-Location
}

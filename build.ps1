param([string]$JavaHome=$env:JAVA_HOME,[string]$DependencyDirectory="$PSScriptRoot/deps")
$ErrorActionPreference='Stop'
if(-not $JavaHome){throw 'Fournir -JavaHome avec un JDK 21 ou plus recent.'}
$javac=Join-Path $JavaHome 'bin/javac.exe'
$java=Join-Path $JavaHome 'bin/java.exe'
$jar=Join-Path $JavaHome 'bin/jar.exe'
$cp=(Join-Path $DependencyDirectory 'powernukkitx-3.0.6.jar')+';'+(Join-Path $DependencyDirectory 'NVaultX-API-1.0.0.jar')
$classes=Join-Path $PSScriptRoot 'build/classes'
$tests=Join-Path $PSScriptRoot 'build/tests'
New-Item -ItemType Directory -Force $classes,$tests | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot/src/main/java" -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
& $javac --release 21 -proc:none -encoding UTF-8 -classpath $cp -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation echouee'}
$testSources=@(Get-ChildItem "$PSScriptRoot/src/test/java" -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
& $javac --release 21 -proc:none -encoding UTF-8 -classpath "$classes;$cp" -d $tests @testSources
if($LASTEXITCODE -ne 0){throw 'Compilation tests echouee'}
& $java "-Dnfactions.root=$PSScriptRoot" -classpath "$tests;$classes;$cp" org.nfactionsx.CoreTests "$PSScriptRoot/build/test-data"
if($LASTEXITCODE -ne 0){throw 'Tests echoues'}
Copy-Item "$PSScriptRoot/src/main/resources/*" $classes -Force
New-Item -ItemType Directory -Force "$classes/META-INF" | Out-Null
Copy-Item "$PSScriptRoot/LICENSE" "$classes/META-INF/LICENSE"
& $jar "-J-Djava.io.tmpdir=$PSScriptRoot/build" --create --file "$PSScriptRoot/NFactionsX-1.0.0.jar" -C $classes .
if($LASTEXITCODE -ne 0){throw 'Creation JAR echouee'}
& $jar "-J-Djava.io.tmpdir=$PSScriptRoot/build" --create --file "$PSScriptRoot/NFactionsX-API-1.0.0.jar" -C $classes org/nfactionsx/api -C $classes META-INF/LICENSE
if($LASTEXITCODE -ne 0){throw 'Creation API echouee'}
Write-Output 'NFactionsX compile et teste.'

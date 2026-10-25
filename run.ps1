$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$mainClass = if ($args.Count -gt 0) { $args[0] } else { "com.client.Main" }

# Maven descarrega JavaFX i compila el projecte
mvn compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# JavaFX 22 per a Windows utilitza l'arquitectura x64
$javaSettings = cmd /c "java -XshowSettings:properties -version 2>&1"
$javaArch = ($javaSettings | Select-String '^\s*os.arch = (.*)$').Matches.Groups[1].Value
if ($javaArch -notin @("amd64", "x86_64")) {
    Write-Error "Arquitectura Java no compatible: $javaArch"
    exit 1
}

# Utilitza només els JAR de JavaFX 22 per a Windows
$fxJars = foreach ($module in @("base", "controls", "fxml", "graphics")) {
    $jar = "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-$module\22\javafx-$module-22-win.jar"
    if (-not (Test-Path $jar)) {
        Write-Error "No es pot trobar JavaFX: $jar"
        exit 1
    }
    $jar
}
$fxPath = $fxJars -join ";"

# Llibreries addicionals (WebSocket + JSON + SLF4J) necessaries per al client i el servidor
$m2 = "$env:USERPROFILE\.m2\repository"
$libJars = @(
    "$m2\org\java-websocket\Java-WebSocket\1.5.7\Java-WebSocket-1.5.7.jar",
    "$m2\org\json\json\20240303\json-20240303.jar",
    "$m2\org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar"
)
foreach ($jar in $libJars) {
    if (-not (Test-Path $jar)) {
        Write-Error "No es pot trobar la llibreria: $jar"
        exit 1
    }
}
$cp = "target/classes;" + ($libJars -join ";")

java --module-path $fxPath --add-modules javafx.controls,javafx.fxml `
    -cp $cp $mainClass
exit $LASTEXITCODE

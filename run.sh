#!/bin/bash
set -e

cd "$(dirname "$0")"
mainClass=${1:-com.client.Main}

# Maven descarrega JavaFX i compila el projecte
mvn compile

# Tria la mateixa arquitectura que utilitza Java
javaArch=$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*os.arch = / { print $3 }')
case "$(uname -s)" in
    Darwin) fxPlatform="mac" ;;
    Linux)  fxPlatform="linux" ;;
    *) echo "Sistema no compatible amb aquest script."; exit 1 ;;
esac
case "$javaArch" in
    aarch64|arm64) fxPlatform="${fxPlatform}-aarch64" ;;
    amd64|x86_64) ;;
    *) echo "Arquitectura Java no compatible: $javaArch"; exit 1 ;;
esac

# Utilitza només els JAR de JavaFX 22 d'aquest sistema
fxPath=""
for module in base controls fxml graphics; do
    jar="$HOME/.m2/repository/org/openjfx/javafx-$module/22/javafx-$module-22-$fxPlatform.jar"
    if [[ ! -f "$jar" ]]; then
        echo "No es pot trobar JavaFX: $jar"
        exit 1
    fi
    fxPath="${fxPath:+$fxPath:}$jar"
done

# Llibreries addicionals (WebSocket + JSON) necessaries per al client i el servidor
m2="$HOME/.m2/repository"
libPath="$m2/org/java-websocket/Java-WebSocket/1.5.7/Java-WebSocket-1.5.7.jar"
libPath="$libPath:$m2/org/json/json/20240303/json-20240303.jar"
libPath="$libPath:$m2/org/slf4j/slf4j-api/2.0.3/slf4j-api-2.0.3.jar"

# Mode "build": empaqueta el servidor en un JAR autocontingut (per al deploy a Proxmox).
# El JAR inclou les classes del servidor i les dependencies (WebSocket, JSON, SLF4J
# i la part de JavaFX que fa servir: javafx.geometry) perquè a la VM remota
# només cal executar "java -jar server-package.jar" sense cap instal·lació extra.
if [[ "${2:-}" == "build" ]]; then
    jarName="target/server-package.jar"
    stage="target/fatjar"
    rm -rf "$stage" "$jarName"
    mkdir -p "$stage"

    cp -r target/classes/. "$stage"/
    for jar in \
        "$m2/org/java-websocket/Java-WebSocket/1.5.7/Java-WebSocket-1.5.7.jar" \
        "$m2/org/json/json/20240303/json-20240303.jar" \
        "$m2/org/slf4j/slf4j-api/2.0.3/slf4j-api-2.0.3.jar" \
        "$m2/org/openjfx/javafx-base/22/javafx-base-22-$fxPlatform.jar" \
        "$m2/org/openjfx/javafx-graphics/22/javafx-graphics-22-$fxPlatform.jar"; do
        if [[ ! -f "$jar" ]]; then
            echo "No es pot trobar la llibreria: $jar"
            exit 1
        fi
        unzip -oq "$jar" -d "$stage"
    done

    # A la VM remota el JAR s'executa alclass-path: els module-info.class i
    # manifestos dels JARs originals només hi farien nosa.
    rm -f "$stage/module-info.class" "$stage/META-INF/MANIFEST.MF"

    jar --create --file "$jarName" --main-class com.server.Main -C "$stage" .
    rm -rf "$stage"
    echo "Empaquetat: $jarName"
    exit 0
fi

exec java --module-path "$fxPath" --add-modules javafx.controls,javafx.fxml \
    -cp "target/classes:$libPath" "$mainClass"

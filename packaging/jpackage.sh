#!/usr/bin/env bash
# Builds a native installer (or app image) for the current OS with jpackage, bundling a Java runtime.
#   packaging/jpackage.sh [type] [version]
#   type: dmg | pkg | msi | exe | deb | rpm | app-image   (default: app-image)
# Run `mvn -pl app -am package -DskipTests` first. Output goes to dist/.
set -euo pipefail
cd "$(dirname "$0")/.."

TYPE="${1:-app-image}"
VERSION="${2:-$(awk '/<artifactId>supermarket-manager-parent<\/artifactId>/ {getline; gsub(/.*<version>|<\/version>.*/, ""); print; exit}' pom.xml)}"
[[ "$VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]] || { echo "Version '$VERSION' must be 1-3 numbers, e.g. 1.2.0" >&2; exit 1; }
if [ "$(uname -s)" = Darwin ] && [ "${VERSION%%.*}" -eq 0 ]; then
  echo "macOS bundles need a version starting at 1 (got $VERSION)" >&2; exit 1
fi
NAME="Supermarket Manager"
JAR=app/target/supermarket-manager.jar
[ -f "$JAR" ] || { echo "Missing $JAR - build it first: mvn -pl app -am package -DskipTests" >&2; exit 1; }

INPUT=app/target/jpackage-input
rm -rf "$INPUT" && mkdir -p "$INPUT" dist
cp "$JAR" "$INPUT/"

ARGS=(--type "$TYPE" --name "$NAME" --app-version "$VERSION" --dest dist
      --input "$INPUT" --main-jar supermarket-manager.jar
      --main-class org.springframework.boot.loader.launch.JarLauncher
      --vendor "Supermarket Manager contributors"
      --description "Pantry targets, item rules, price comparison and supermarket baskets"
      --java-options "-Xmx512m" --java-options "-Dfile.encoding=UTF-8"
      --jlink-options "--strip-debug --no-man-pages --no-header-files")

case "$(uname -s)" in
  Darwin)
    ARGS+=(--icon packaging/icons/icon.icns --mac-package-identifier com.supermarketmanager.app --mac-package-name "Supermarket Mgr") ;;
  MINGW*|MSYS*|CYGWIN*)
    ARGS+=(--icon packaging/icons/icon.ico)
    if [ "$TYPE" = msi ] || [ "$TYPE" = exe ]; then
      ARGS+=(--win-menu --win-shortcut --win-dir-chooser --win-per-user-install --win-menu-group "Supermarket Manager"
             --win-upgrade-uuid 6f3a2c1e-5b7d-4e8f-9a0b-1c2d3e4f5a6b)
    fi ;;
  Linux)
    ARGS+=(--icon packaging/icons/icon.png)
    if [ "$TYPE" = deb ] || [ "$TYPE" = rpm ]; then
      ARGS+=(--linux-shortcut --linux-menu-group Office --linux-package-name supermarket-manager --linux-app-category misc)
    fi ;;
esac

jpackage "${ARGS[@]}"
ls -la dist

#!/usr/bin/env sh
# Text-only bootstrap. Verifies the official distribution before executing it.
set -eu
project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
version=8.11.1
expected=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
gradle_bin="$project_dir/.tools/gradle-$version/bin/gradle"
if [ ! -x "$gradle_bin" ]; then
    command -v java >/dev/null 2>&1 || [ -n "${JAVA_HOME:-}" ] || { echo 'Install JDK 17 or set JAVA_HOME.' >&2; exit 1; }
    mkdir -p "$project_dir/.tools"
    archive="$project_dir/.tools/gradle-$version-bin.zip"
    curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$archive.part"
    if command -v sha256sum >/dev/null 2>&1; then
        actual=$(sha256sum "$archive.part" | cut -d ' ' -f 1)
    else
        actual=$(shasum -a 256 "$archive.part" | cut -d ' ' -f 1)
    fi
    [ "$actual" = "$expected" ] || { echo 'Gradle checksum verification failed.' >&2; exit 1; }
    mv "$archive.part" "$archive"
    unzip -q -o "$archive" -d "$project_dir/.tools"
fi
exec "$gradle_bin" -p "$project_dir" "$@"


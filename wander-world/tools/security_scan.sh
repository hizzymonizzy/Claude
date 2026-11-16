#!/usr/bin/env bash
# Privacy & security scan of everything third-party in the app. Run before merging any new
# dependency, asset pack or SDK. See docs/security.md for what each section should look like.
#
#   ANDROID_HOME=/path/to/sdk tools/security_scan.sh
set -euo pipefail
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/debug/app-debug.apk

echo "== 1. Build with dependency verification (fails if any library changed without review)"
./gradlew -q :app:assembleDebug

echo; echo "== 2. Runtime libraries (group → count)"
./gradlew -q :app:dependencies --configuration debugRuntimeClasspath \
  | grep -oE "[a-z0-9.\-]+:[a-zA-Z0-9.\-]+:[0-9][^ ()]*" | awk -F: '{print $1}' | sort | uniq -c | sort -rn

M=app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
echo; echo "== 3. Permissions in the final app (libraries can add these)"
grep -oE 'uses-permission[^>]*android:name="[^"]+"' "$M" | sed 's/.*name="//;s/"//' | sort

echo; echo "== 4. Components other apps can reach (exported)"
grep -B2 -A2 'exported="true"' "$M" | grep -oE 'android:name="[^"]+"' | sort -u

echo; echo "== 5. Web hosts compiled into the app (code + native libraries)"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -q -o "$APK" 'classes*.dex' 'lib/*' -d "$TMP"
for f in "$TMP"/classes*.dex "$TMP"/lib/*/*.so; do strings -n 8 "$f"; done \
  | grep -oE "https?://[A-Za-z0-9._~:-]+" | sort | uniq -c | sort -rn \
  | grep -vE "schemas\.android|www\.w3\.org|xml\.org|xmlpull|ns\.adobe|purl\.org|apache\.org|java\.sun|json-schema|opengis"

echo; echo "== 6. Our own network calls (must match docs/security.md)"
grep -rnoE '"https://[^"]+"' app/src/main/java | sort -u

echo; echo "== 7. Risky APIs in our code (should be empty)"
grep -rnE "WebView|addJavascriptInterface|Runtime\.getRuntime\(\)\.exec|DexClassLoader|rawQuery\(|execSQL\(\"[^\"]*\\\$" app/src/main/java || echo "(none)"

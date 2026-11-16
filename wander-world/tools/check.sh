#!/usr/bin/env bash
# The gate every change passes before it is committed, and what CI runs on every push.
#   1. engine + app unit tests (rules, DB migrations, Overpass parsing, map layers)
#   2. app build (dependency verification on)   3. lint (errors fail)
#   4. privacy/security policy: permissions, exported components, hosts and risky APIs must match
#      tools/policy/*.txt exactly. A difference fails until the policy file AND docs/security.md are updated.
#
#   ANDROID_HOME=/path/to/sdk tools/check.sh
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0

echo "== 1-3. Tests, build, lint"
./gradlew -q :engine:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

M=app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
policy() { grep -vE '^\s*(#|$)' "tools/policy/$1"; }
compare() { # name, policy file, actual list
  local want have
  want=$(policy "$2" | sort -u); have=$(echo "$3" | grep -v '^$' | sort -u)
  if [ "$want" != "$have" ]; then
    echo "!! $1 differs from tools/policy/$2:"; diff <(echo "$want") <(echo "$have") | sed 's/^/   /' || true; fail=1
  else echo "ok $1"; fi
}

echo; echo "== 4. Privacy & security policy"
compare "permissions" permissions.txt "$(grep -oE 'uses-permission[^>]*android:name="[^"]+"' "$M" | sed 's/.*name="//;s/"//')"
compare "exported components" exported.txt "$(grep -B2 -A2 'exported="true"' "$M" | grep -oE 'android:name="[^"]+"' | sed 's/android:name="//;s/"//')"
compare "hosts in our code" hosts.txt "$(grep -rhoE '"https://[^"]+"' app/src/main/java | grep -oE 'https://[^/"]+')"
risky=$(grep -rnE "WebView|addJavascriptInterface|Runtime\.getRuntime\(\)\.exec|DexClassLoader|rawQuery\(|execSQL\(\"[^\"]*\\\$" app/src/main/java || true)
if [ -n "$risky" ]; then echo "!! risky APIs:"; echo "$risky" | sed 's/^/   /'; fail=1; else echo "ok risky APIs (none)"; fi
grep -q 'android:allowBackup="false"' "$M" && echo "ok backups off" || { echo "!! allowBackup is not false"; fail=1; }
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml && echo "ok https only" || { echo "!! cleartext allowed"; fail=1; }

echo
if [ $fail -ne 0 ]; then echo "CHECK FAILED"; exit 1; fi
echo "All checks passed."

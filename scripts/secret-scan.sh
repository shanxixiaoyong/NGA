#!/usr/bin/env bash
set -euo pipefail

scan_root="${1:-.}"

if command -v rg >/dev/null 2>&1; then
  scanner=(rg --hidden --glob '!.git/**' --glob '!references/**' --glob '!.gradle/**' --glob '!.toolchains/**' --glob '!.android-sdk/**' --glob '!**/build/**' --line-number --ignore-case)
else
  scanner=(grep -RIn --exclude-dir=.git --exclude-dir=references --exclude-dir=.gradle --exclude-dir=.toolchains --exclude-dir=.android-sdk --exclude-dir=build)
fi

patterns=(
  'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
  "(password|passwd|captcha)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{6,}['\"]"
  "(cookie|set-cookie|authorization)[[:space:]]*[:=][[:space:]]*['\"][A-Za-z0-9_%./+=:-]{12,}['\"]"
  "['\"](cookie|set-cookie|authorization)['\"][[:space:]]*(to|,)[[:space:]]*['\"][A-Za-z0-9_%./+=:-]{12,}['\"]"
  "(api[_-]?key|secret[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][A-Za-z0-9_-]{12,}['\"]"
  'AKIA[0-9A-Z]{16}'
)

failed=0
for pattern in "${patterns[@]}"; do
  if "${scanner[@]}" "$pattern" "$scan_root"; then
    failed=1
  fi
done

# Product-source policy checks are kept separate from the general secret
# patterns so research documents can retain evidence of removed upstream
# behavior without failing the release scan.
if command -v rg >/dev/null 2>&1; then
  product_policy_patterns=(
    'X-User-Agent[^\n]*Nga_Official'
    "(storePassword|keyPassword)[[:space:]]+['\"][^'\"]+['\"]"
    'CrashReport\.putUserData'
    '(NLog|LogUtils|Logger)\.[[:alpha:]]+\([^;\n]*(getDataString\(\)|toJSONString\(\)|getStackTraceString\(|getMessage\(\)|\+\s*(js|jsonString|content|response|responseBody|body|cookie|url|uri|data|s)\b|\b(js|jsonString|content|response|responseBody|body|cookie|url|uri|data|s)\s*\+|\$\{[^}]*(js|jsonString|content|response|responseBody|body|cookie|url|uri|data))'
    '(NLog|LogUtils|Logger)\.[[:alpha:]]+\([[:space:]]*(js|jsonString|content|response|responseBody|body|cookie|url|uri|data|s)[[:space:]]*\)'
  )
  for pattern in "${product_policy_patterns[@]}"; do
    if rg --glob '!**/build/**' --line-number --ignore-case \
      "$pattern" "$scan_root"/lib_* "$scan_root"/nga_phone_base_3.0; then
      failed=1
    fi
  done

  # Legacy mutation transports must name and consult the foundation mutation
  # gate. Restrict this check to production sources so test fixtures can model
  # unsafe transports without weakening the release scan.
  while IFS= read -r mutation_source; do
    if ! rg -q 'FoundationMutationGate' "$mutation_source"; then
      echo "Legacy mutation transport bypasses FoundationMutationGate: $mutation_source" >&2
      failed=1
    fi
  done < <(rg -l \
    'new HttpPostClient\(|setRequestMethod\([[:space:]]*"(POST|PUT|PATCH|DELETE)"|setDoOutput\([[:space:]]*true[[:space:]]*\)' \
    "$scan_root"/nga_phone_base_3.0/src/main/java || true)

  http_post_client="$scan_root/nga_phone_base_3.0/src/main/java/sp/phone/param/HttpPostClient.java"
  avatar_upload="$scan_root/nga_phone_base_3.0/src/main/java/sp/phone/task/AvatarFileUploadTask.java"
  if ! rg -Uq 'if \(!FoundationMutationGate\.isAllowed\(operation\)\)[[:space:]]*\{[[:space:]]*return null;' \
    "$http_post_client"; then
    echo "HttpPostClient must deny before parsing or opening a URL." >&2
    failed=1
  fi
  if ! rg -Uq 'if \(!FoundationMutationGate\.isAllowed\([[:space:]]*FoundationMutationGate\.Operation\.AVATAR_FILE_UPLOAD\)\)[[:space:]]*\{[[:space:]]*return null;' \
    "$avatar_upload"; then
    echo "AvatarFileUploadTask must deny before opening its upload connection." >&2
    failed=1
  fi
fi

# Source scans intentionally exclude Gradle build outputs. When a debug APK is
# present, scan its textual dex/resource strings separately so the release gate
# also covers packaged artifacts without treating binary noise as source.
apk_path="${APK_TO_SCAN:-$scan_root/nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk}"
if [[ -f "$apk_path" ]] && command -v unzip >/dev/null 2>&1 && command -v strings >/dev/null 2>&1; then
  apk_strings_file="$(mktemp)"
  trap 'rm -f "$apk_strings_file"' EXIT
  while IFS= read -r entry; do
    case "$entry" in
      classes*.dex|resources.arsc|AndroidManifest.xml|assets/*|res/*)
        unzip -p "$apk_path" "$entry" 2>/dev/null || true
        ;;
    esac
  done < <(unzip -Z1 "$apk_path") | strings -a >"$apk_strings_file"
  for pattern in "${patterns[@]}"; do
    if grep -Eiq "$pattern" "$apk_strings_file"; then
      echo "Potential packaged secret material found in $apk_path (pattern: $pattern)." >&2
      failed=1
    fi
  done
fi

if [[ "$failed" -ne 0 ]]; then
  echo "Potential secret material found." >&2
  exit 1
fi

echo "Secret scan passed."

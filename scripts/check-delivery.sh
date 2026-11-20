#!/usr/bin/env bash
# Checks that AI Studio (or any external) sync has not reverted delivery essentials.
# Run after pulling/exporting code from AI Studio:
#   ./scripts/check-delivery.sh          # static checks only (fast)
#   ./scripts/check-delivery.sh --build  # also run unit tests
#
# Exit code 0 = all checks pass; 1 = at least one regression detected.
set -uo pipefail
cd "$(dirname "$0")/.."

PASS=0
FAIL=0

check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    printf '  ✅ %s\n' "$desc"
    PASS=$((PASS + 1))
  else
    printf '  ❌ %s\n' "$desc"
    FAIL=$((FAIL + 1))
  fi
}

echo "CardWallet 交付完整性检查"
echo "========================="

echo "[1] Firebase 配置"
check "app/google-services.json 存在" test -f app/google-services.json
check "google-services.json 包名正确" \
  grep -q "com.aistudio.nfcshield.kvnxqr" app/google-services.json
check "google-services.json 是合法 JSON" \
  python3 -c "import json; json.load(open('app/google-services.json'))"

echo "[2] 编译兼容性(无配置也可编译)"
check "FirebaseBootstrap 使用动态资源查找(getIdentifier)" \
  grep -q "getIdentifier" app/src/main/java/com/aistudio/nfcshield/FirebaseBootstrap.kt
check "FirebaseBootstrap 未回退到显式 R.string.google_app_id" \
  bash -c '! grep -q "R.string.google_app_id" app/src/main/java/com/aistudio/nfcshield/FirebaseBootstrap.kt'

echo "[3] App Check"
check "ShieldVaultApplication 调用 AppCheckInstaller.install" \
  grep -q "AppCheckInstaller.install" app/src/main/java/com/aistudio/nfcshield/ShieldVaultApplication.kt
check "release AppCheckInstaller(Play Integrity)存在" \
  test -f app/src/release/java/com/aistudio/nfcshield/AppCheckInstaller.kt
check "debug AppCheckInstaller(Debug provider)存在" \
  test -f app/src/debug/java/com/aistudio/nfcshield/AppCheckInstaller.kt
check "build.gradle.kts 含 playintegrity 依赖" \
  grep -q "firebase.appcheck.playintegrity" app/build.gradle.kts

echo "[4] 包名与可执行位"
check "namespace 为 com.aistudio.nfcshield" \
  grep -q 'namespace = "com.aistudio.nfcshield"' app/build.gradle.kts
check "源码无 com.example 残留" \
  bash -c '! grep -rq "com\.example" app/src --include="*.kt"'
check "gradlew 可执行" test -x gradlew
check "scripts/build-release.sh 可执行" test -x scripts/build-release.sh

echo "[5] 安全修复核心"
check "VaultCryptoEngine 含 KDF_SHA256 与 verifier v2" \
  bash -c 'grep -q "KDF_SHA256" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt && grep -q "VERIFIER_V2_DOMAIN" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt'
check "Aes256GcmCryptoUtil 含版本化解密(CURRENT_CRYPTO_VERSION)" \
  grep -q "CURRENT_CRYPTO_VERSION" app/src/main/java/com/aistudio/nfcshield/crypto/Aes256GcmCryptoUtil.kt
check "LocalVaultStore 含 sealActiveHceCard(quick 标志职责分离)" \
  grep -q "sealActiveHceCard" app/src/main/java/com/aistudio/nfcshield/data/local/LocalVaultStore.kt
check "firestore.rules 含 120000 迭代下限" \
  grep -q "120000" firestore.rules
check "firestore.rules 含计数器 +1 原子规则" \
  grep -q "emulationCount + 1" firestore.rules

echo "[6] 工程化"
check "CI 工作流存在" test -f .github/workflows/ci.yml
check "规则测试 harness 存在" test -f package.json
check "交付文档存在" test -f docs/delivery.md

if [[ "${1:-}" == "--build" ]]; then
  echo "[7] 单元测试"
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  if ./gradlew testDebugUnitTest --console=plain >/tmp/cw-check-build.log 2>&1; then
    printf '  ✅ testDebugUnitTest 通过\n'
    PASS=$((PASS + 1))
  else
    printf '  ❌ testDebugUnitTest 失败(详见 /tmp/cw-check-build.log)\n'
    FAIL=$((FAIL + 1))
  fi
fi

echo "========================="
echo "结果: $PASS 通过, $FAIL 失败"
if [[ $FAIL -gt 0 ]]; then
  echo "⚠️  检测到回退。常见修复:"
  echo "   - google-services.json 丢失: git show <上次正常提交>:app/google-services.json > app/google-services.json"
  echo "   - R.string 回退: 将 FirebaseBootstrap.safeGetString 改回 getIdentifier 动态查找"
  echo "   - App Check 丢失: 在 ShieldVaultApplication 的 bootstrap 后加 AppCheckInstaller.install(this)"
  echo "   - 可执行位: chmod +x gradlew scripts/build-release.sh"
  exit 1
fi
echo "✅ 交付完整性正常"
exit 0
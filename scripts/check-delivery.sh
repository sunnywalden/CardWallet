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

echo "[6] 设备绑定与 v1 淘汰(2026-11-20)"
check "VaultCryptoEngine 含设备绑定 DeviceKeySealer 与 schema v3" \
  bash -c 'grep -q "interface DeviceKeySealer" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt && grep -q "ANDROID_KEYSTORE" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt && grep -q "CURRENT_SCHEMA_VERSION = 3" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt'
check "v1 verifier/解锁路径已移除" \
  bash -c '! grep -q "computePinVerifierV1" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt && ! grep -q "KDF_SHA1_LEGACY" app/src/main/java/com/aistudio/nfcshield/crypto/VaultCryptoEngine.kt'
check "卡密文只接受 cryptoVersion 2" \
  grep -q "KNOWN_CRYPTO_VERSIONS = setOf(CURRENT_CRYPTO_VERSION)" app/src/main/java/com/aistudio/nfcshield/crypto/Aes256GcmCryptoUtil.kt
check "firestore.rules 含 v3 设备绑定分支" \
  bash -c 'grep -q "isValidVaultConfigV3" firestore.rules && grep -q "schemaVersion == 3" firestore.rules && grep -q "device-bound:v3" firestore.rules'
check "firestore.rules 拒绝 v1 卡密文" \
  bash -c 'grep -q "data.cryptoVersion == 2" firestore.rules && ! grep -q "cryptoVersion >= 1" firestore.rules'

echo "[7] 同步容灾与调试令牌(2026-11-20)"
check "Debug AppCheck 安装器同步写入调试令牌" \
  bash -c 'grep -q "com.google.firebase.appcheck.debug.DEBUG_SECRET" app/src/debug/java/com/aistudio/nfcshield/AppCheckInstaller.kt && grep -q "\.commit()" app/src/debug/java/com/aistudio/nfcshield/AppCheckInstaller.kt'
check "Release AppCheck 安装器零调试令牌残留" \
  bash -c '! grep -q "DebugAppCheckProviderFactory\|DEBUG_SECRET" app/src/release/java/com/aistudio/nfcshield/AppCheckInstaller.kt'
check "BuildConfig 仅 debug 注入 APPCHECK_DEBUG_TOKEN" \
  bash -c 'grep -q "APPCHECK_DEBUG_TOKEN" app/build.gradle.kts && grep -q "Release APKs must never carry a debug App Check token" app/build.gradle.kts'
check "VaultSavedStateCodec 序列化 v3 deviceBinding" \
  grep -q 'put("deviceBinding"' app/src/main/java/com/aistudio/nfcshield/data/model/VaultSavedStateCodec.kt
check "LocalVaultStore 含加密离线缓存与登出清理" \
  bash -c 'grep -q "readEncryptedVaultCache" app/src/main/java/com/aistudio/nfcshield/data/local/LocalVaultStore.kt && grep -q "CACHE_CARDS" app/src/main/java/com/aistudio/nfcshield/data/local/LocalVaultStore.kt'
check "仓储层 10s 令牌超时与阶梯退避" \
  bash -c 'grep -q "TOKEN_REFRESH_TIMEOUT_MS = 10_000L" app/src/main/java/com/aistudio/nfcshield/data/remote/FirestoreVaultRepository.kt && grep -q "RETRY_BASE_DELAY_MS shl" app/src/main/java/com/aistudio/nfcshield/data/remote/FirestoreVaultRepository.kt'
check "ViewModel 暴露 cloudSyncIssue 且 UI 接线" \
  bash -c 'grep -q "cloudSyncIssue" app/src/main/java/com/aistudio/nfcshield/ui/viewmodel/VaultViewModel.kt && grep -q "SyncDiagnosticsCard" app/src/main/java/com/aistudio/nfcshield/ui/screens/VaultLockScreen.kt'

echo "[8] 工程化"
check "CI 工作流存在" test -f .github/workflows/ci.yml
check "规则测试 harness 存在" test -f package.json
check "交付文档存在" test -f docs/delivery.md

if [[ "${1:-}" == "--build" ]]; then
  echo "[9] 单元测试"
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
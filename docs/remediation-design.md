# CardWallet(盾匙 NFC)修复与优化设计方案

> 依据:2026-11-20 设计审查 + 全量代码正确性扫描的合并结论(H1–H6 / M1–M12 / L1–L2 及打磨项)。
> 原则:**安全路径 fail-closed;降级必须显式且用户知情;云端 schema 变更必须可迁移、可回滚。**
> 状态:**已全量实施**(2026-11-20)。P0/P1/P2/P3 均落地并通过验证,例外见 §7 闭环记录。
> 所有代码片段为示意,实际实现以仓库代码为准。

---

## 0. 总体策略

### 0.1 分期

| 期 | 内容 | 是否动云端 schema | 预估工作量 |
|---|---|---|---|
| **P0 热修** | H6、M10、M12、M7、M8、M11、H3、L1、quick 标志默认值 | 否 | 0.5–1 天 |
| **P1 加密与数据完整性** | H1+H4(KDF 显式化+verifier v2)、H5(cryptoVersion)、H2(认证绑定降级)、M1、M2、M3 | 是(schema v2,双读兼容) | 2–3 天 |
| **P2 NFC 健壮性** | M4、M5、M9 | 否 | 1–2 天 |
| **P3 工程化** | L2(规则测试可运行+CI)、App Check、namespace 重命名、威胁模型文档、Firebase 缺失 fail-fast | 否 | 1–2 天 |

### 0.2 兼容性总纲(P1 核心)

云端存在三类存量数据,全部采用 **"旧格式可读 → 解锁成功后透明升级 → 规则双格式放行"** 模式:

| 存量 | 识别方式 | 升级动作 | 升级时机 |
|---|---|---|---|
| vault/config v1(无 kdfAlgorithm、verifier v1) | `schemaVersion == 1` | 用旧算法/旧 verifier 解锁成功后,以 SHA-256 + verifier v2 重新封装并 `saveVaultConfig(isUpdate=true)` | PIN 解锁成功时 |
| 卡密文 v1(无 AAD) | 卡文档无 `cryptoVersion` 字段(默认 1) | 以 DEK 重新加密(带 owner AAD),写 `cryptoVersion=2` | 解锁后后台批量(每批 ≤20,失败不阻塞 UI) |
| 本地 AES_KEYSTORE 旧封装(无认证绑定) | `SealedDekRecord.mode == AES_KEYSTORE` | 下次 PIN 解锁成功时以新的认证绑定密钥重新封装 | PIN 解锁成功时 |

规则过渡期:`schemaVersion in [1,2]`、`cryptoVersion` 可选(缺省按 1)、`kdfAlgorithm` 可选;**v1 写入只允许出现在 update(迁移),create 一律要求 v2**。设定淘汰里程碑:两个版本周期后 create/update 全面拒绝 v1(需 owner 确认存量清零)。

---

## P0 热修(无 schema 变更)

### H6 — 快捷开关"暂停 HCE"被静默撤销 🔴

**根因**:`syncActiveHceFromCards` 在 `cardsState` 每次发射时调用 `saveActiveHceCard(cfg)`,后者无条件 `QUICK_HCE_ENABLED=true`。

**设计**:
1. `LocalVaultStore` 拆分职责:
```kotlin
// 只写密文,不碰 quick 标志
suspend fun sealActiveHceCard(config: ActiveHceCardConfig) {
    dataStore.edit { prefs ->
        val (cipher, iv) = VaultCryptoEngine.sealWithHardwareKeystore(
            config.toJsonString().toByteArray(Charsets.UTF_8))
        prefs[Keys.ACTIVE_HCE_CIPHER] = cipher
        prefs[Keys.ACTIVE_HCE_IV] = iv
    }
}
// 保留原 saveActiveHceCard 语义仅供"用户显式激活/停用"调用
```
2. `syncActiveHceFromCards` 改为**差异写入**:与 `HceRuntimeBus.getCachedActiveCard()` 比较 `(cardId, aidHex, apduResponseHex, requireVaultUnlocked)`,完全一致则直接返回;变化时只调 `sealActiveHceCard`。
3. 缓存填充尊重 quick 标志:
```kotlin
val quickEnabled = localStore.isQuickHceEnabled()
HceRuntimeBus.updateCachedActiveCard(if (quickEnabled) cfg else null)
```
4. `isQuickHceEnabled()`/`readActiveHceCard()` 的缺省值统一为 **`false`**(无显式开启记录 = 不模拟,fail-closed);`saveActiveHceCard(非null)` 仍显式置 true(用户主动激活语义)。

**回归测试**:模拟 TOGGLE_OFF → 触发 cardsState 重新发射 → 断言 `quick_hce_enabled` 仍为 false、总线缓存为 null。

### M10 — 正确 PIN 后崩溃 / 改 PIN 回调丢失 🟡

**设计**:三处 seal 调用统一包裹,且回调必达:
```kotlin
private suspend fun trySealForBiometric(dek: ByteArray) {
    if (!securityPrefs.value.biometricEnabled) return
    runCatching { localStore.sealDekForBiometric(currentUserId, dek) }
        .onSuccess { _hasBiometricSealedKey.value = true }
        .onFailure { Log.w(TAG, "biometric seal failed; PIN unlock unaffected", it) }
}
```
- `unlockWithPin`、`changeVaultPin`、`setupVaultPin` 全部改用它。
- `changeVaultPin` 外层补 `try/catch(CancellationException 重抛)/catch(Exception → onResult(failure))`,保证 `onResult` 在任何分支都被调用;云端已轮换但本地封装失败时,提示"PIN 已更新,生物识别缓存将在下次解锁时重建"。

**测试**:注入抛异常的 FakeLocalStore,断言解锁成功、改 PIN 回调收到 success(封装失败仅降级)。

### M12 — 生物封装在主线程 🟢

`trySealForBiometric` 内部 `withContext(Dispatchers.Default)` 执行 Keystore 操作与 DataStore 写。与 M10 同一改动落地。

### M7 — ViewModel 销毁不清零 DEK 🟡

```kotlin
override fun onCleared() {
    _dekState.value?.fill(0)
    _dekState.value = null
    HceRuntimeBus.setVaultUnlocked(false)
    super.onCleared()
}
```
注意:不清 `cachedActiveCard`(HCE 锁屏刷卡依赖它,且其内容已硬件封装落盘);`prepareForSignOut` 维持现有更彻底的清理。

### M8 — 自动锁屏墙钟可回拨 🟡

`backgroundTimestampMillis` 改用 `SystemClock.elapsedRealtime()`;`shouldLockAfterBackground` 逻辑不变(纯函数,已有测试基础)。锁屏倒计时(M1)同样基于 elapsedRealtime。

### M11 — 登出后安全偏好跨账号残留 🟡

`clearSensitiveLocalSession` 追加移除 `BIOMETRIC_ENABLED`、`AUTO_LOCK_MINUTES`、`REQUIRE_UNLOCK_FOR_HCE`(恢复默认值 = 生物开启/5 分钟/不强制)。
备选(更彻底但改动大):偏好键加 `_$uid` 后缀按账号隔离。**推荐前者**:偏好是设备级便利设置,登出即重置语义清晰。

### H3 — 错误日志泄露 PII 🔴

`FirestoreErrorHandler` 重写:
```kotlin
val uidHash = currentUser?.uid?.let {
    MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
        .joinToString("") { b -> "%02x".format(b) }.take(12)
}
// 只输出:uidHash、errorCode(FirebaseFirestoreException.code)、operationType、path、error.message
// 删除:email、emailVerified、tenantId、providerData 全部字段
```
另:`Log.e` 全量输出仅在 `BuildConfig.DEBUG`;release 只留 code+operation。全局 grep 确认无其他 email/uid 明文日志。

### L1 — 备注超长导致晦涩权限错误 🟢

三层防线:
1. UI:`NfcScanScreen`/`CardDetailScreen` 备注输入 `maxLength = 1500`(TextField `onValueChange` 截断 + 字数提示)。
2. ViewModel:`saveNewCard`/`updateCard` 前置校验 `secrets.notes.length <= 1500`,超限返回友好错误。
3. 加密后尺寸预检:`enc.encryptedPayloadBase64.length <= 8192`,超限返回"卡片数据过大,请精简备注"。

### quick 标志默认值不一致(打磨项)

并入 H6 第 4 点统一处理。

---

## P1 加密与数据完整性(schema v2)

### H1 + H4 — KDF 显式化、防降级、参数完整性绑定 🔴

**现状**:SHA-256 不可用时静默回退 SHA-1;算法未持久化 → 跨 API 级别设备永久"PIN 错误";`kdfIterations` 可被持有会话者降至 1 万。

**设计**:

1. **算法显式化**(保留 minSdk 24,不强制升级设备门槛):
```kotlin
data class WrappedDekPayload(
    ...,
    val kdfAlgorithm: String = KDF_SHA256,   // 新增
    val schemaVersion: Int = 2               // 1 → 2
)
const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
const val KDF_SHA1_LEGACY = "PBKDF2WithHmacSHA1"
```
2. **deriveKek 按字段选算法,不再 try/catch 降级**:
```kotlin
private fun deriveKek(pin: CharArray, salt: ByteArray, iterations: Int, algorithm: String): SecretKey {
    require(algorithm in setOf(KDF_SHA256, KDF_SHA1_LEGACY)) { "unsupported kdf" }
    val factory = SecretKeyFactory.getInstance(algorithm) // 不支持即抛 → 上层明确报错
    ...
}
```
新封装一律 SHA-256;API 24–25 设备若不支持 SHA-256,**创建保险库时明确报错**"此设备系统版本过低,无法创建安全保险库"(fail-closed,而不是造一个将来打不开的库)。读取旧 SHA-1 记录仍允许(迁移用途)。
3. **verifier v2 绑定全部 KDF 参数**(堵 H4):
```kotlin
// v2: SHA-256("shield-nfc-pin-verifier-v2" || salt || le32(iterations) || utf8(algorithm) || kek)
// v1: 旧公式,仅用于识别/解锁存量
```
解锁流程:先按 v2 校验;失败且 `schemaVersion==1` 时按 v1 校验;v1 成功 → 立即以 SHA-256+verifier v2 重封装并同步云端(透明迁移)。
4. **迭代数下限**:客户端读取 v2 配置时 `require(kdfIterations >= 120_000)`;v1 迁移路径豁免但升级后写回 120k。
5. **规则同步**(firestore.rules):
```
data.kdfIterations is int && data.kdfIterations >= 120000 && data.kdfIterations <= 1000000
// 过渡期:允许 schemaVersion==1 的 update 保留 >=10000;create 一律 v2 + >=120000
// kdfAlgorithm in ['PBKDF2WithHmacSHA256','PBKDF2WithHmacSHA1'](后者仅 update 迁移)
```
6. **VaultConfigDoc / 规则白名单 / saveVaultConfig 字段映射**同步增加 `kdfAlgorithm`。

**测试**:
- 跨算法迁移:构造 SHA-1 v1 fixture → 解锁成功 → 断言云端被重写为 SHA-256 v2。
- 篡改迭代数(120k→10k)→ verifier v2 校验失败,拒绝解锁。
- 不支持 SHA-256 的环境创建保险库 → 明确错误而非静默 SHA-1。

### H5 — 卡密文版本化,废除盲目 AAD 回退 🔴

**设计**:
1. `EncryptedCardDoc` 增加 `val cryptoVersion: Long = 1`;新写入一律 `2`。
2. `decryptNfcCardPayload` 增加 `cryptoVersion` 参数,**按版本精确选择 AAD,删除 try/catch 回退**:
```kotlin
val aad = if (cryptoVersion >= 2) buildCardAad(ownerUserId) else null
// 一次解密,失败即失败(AEADBadTagException → decryptionFailed)
```
3. `VaultViewModel.decryptDoc` 透传 `doc.cryptoVersion`;未知版本(>2)→ `decryptionFailed=true`,UI 显示"数据版本不支持,请升级应用"。
4. **v1 卡迁移**:解锁成功后启动 `migrateLegacyCards()`——遍历 `cryptoVersion<2` 且解密成功的卡,重加密(带 AAD)+ `updateCard(..., cryptoVersion=2)`,每批 ≤20,`runCatching` 包裹,失败静默重试于下次解锁(不阻塞 UI、不弹错)。
5. 规则:`cryptoVersion` 可选 int ∈ [1,2];create 要求 `==2`;update 允许 1→2 单向(`incoming().cryptoVersion >= existing().cryptoVersion`)。

**测试**:v2 记录被移植到另一 uid → 解密失败(不再有无 AAD 回退路径);v1 fixture 迁移后 `cryptoVersion==2` 且可正常解密。

### H2 — 生物识别降级路径认证绑定 🔴

**现状**:无强生物识别设备 → 无认证绑定的 AES 密钥 + 仪式性 BiometricPrompt + 允许 WEAK。

> **实施评审修正(2026-11-20)**:原方案"认证绑定 AES-GCM"不可行——AndroidKeyStore 的
> `setUserAuthenticationRequired(true)` 对**加密端同样生效**,而 DEK 封装发生在 PIN 设置流程
> (无认证上下文),无法加密。改为**沿用 RSA-OAEP 公钥封装**(公钥加密无需认证、私钥解密需认证),
> 只调整密钥策略:无强生物识别时生成 `AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL` 的 RSA 密钥,
> BiometricPrompt 使用组合认证器。逻辑同构、无需新加密原语,且保持"进程内代码无法私自解封"。

**设计**(消灭"进程内任意代码可解封"路径):
1. **降级路径 = 组合认证器 RSA**(API 30+):
```kotlin
// 新 alias: shield_nfc_biometric_rsa_key_v3
KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setKeySize(2048)
    .setDigests(SHA256, SHA1)
    .setEncryptionPaddings(RSA_OAEP)
    .setUserAuthenticationRequired(true)
    .setInvalidatedByBiometricEnrollment(true)
    .setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)
```
封装走公钥(`sealWithBiometricKeystorePublicKey` 同一实现,按 mode 选 alias);解封走
`BiometricPrompt.CryptoObject`。`BiometricSealMode` 改为:
`RSA_CRYPTO_OBJECT`(强生物)· `RSA_DEVICE_CREDENTIAL`(组合)· `AES_KEYSTORE`(旧版,仅读,下次 PIN 解锁升级)。
2. **解锁挑战统一**:`CryptoObjectPrompt` 增加 `deviceCredentialAllowed: Boolean`;`BiometricHelper` 按此构造
   PromptInfo(组合认证器时禁用 negativeButton,这是 BiometricPrompt 的平台约束)。
   `LegacyPrompt` 仅用于旧 AES_KEYSTORE 记录,文案明示"旧版缓存,输入一次 PIN 即可升级"。
3. **认证器收紧**:`ALLOWED_AUTHENTICATORS` 去掉 `BIOMETRIC_WEAK` → `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`。
4. **显式降级告知**:`sealDekForBiometric` 返回实际 mode(失败返回 null);`SecuritySettingsScreen` 与
   锁屏徽标按 mode 显示保障级别:
   - `RSA_CRYPTO_OBJECT` → "硬件密钥 · 每次解锁需生物识别"
   - `RSA_DEVICE_CREDENTIAL` → "硬件密钥 · 每次解锁需生物或锁屏密码"
   - `AES_KEYSTORE` → "旧版设备绑定缓存(安全性较低),输入一次 PIN 即可升级"
   - 无封装 → "仅 PIN 解锁(该设备不支持安全生物识别)"
5. API 24–29 无强生物识别设备:不做不安全降级,封装失败 → 仅 PIN(显式告知)。

**测试**:组合认证器封装/解封路径;旧 AES_KEYSTORE 记录 PIN 解锁后被替换;无封装设备 UI 显示"仅 PIN"。

### M1 — PIN 失败计数持久化 + 指数退避 🟡

DataStore 新键:`pin_failed_attempts`(int)、`pin_lockout_until_elapsed`(long,elapsedRealtime 基准)。
策略:5 次→30s;10 次→5min;15 次及以上→30min(封顶);成功解锁清零。
`unlockWithPin` 入口先读持久化 lockout(进程重启不绕过);`startLockoutTimer` 写盘 + 内存倒计时双轨。
**文档明示**:此为 UI 层速率限制;离线爆破防线是 KDF(H1/H4),不依赖此计数。

### M2 — emulationCount 原子自增 🟡

`recordHceEmulation(userId, cardId)` 改写:
```kotlin
.update(mapOf(
    "emulationCount" to FieldValue.increment(1),
    "lastEmulatedAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()))
```
规则 tap 分支改为:
```
incoming().diff(existing()).affectedKeys().hasOnly(['emulationCount','lastEmulatedAt','updatedAt']) &&
incoming().emulationCount == existing().emulationCount + 1
```
ViewModel 侧去掉 `nextCount` 参数与本地 +1;可选内存合并(200ms 窗口)减少写频。

### M3 — 激活 HCE 原子化 🟡

`saveNewCard` 的 create+去激活合并为单 batch:
```kotlin
val batch = db.batch()
batch.set(newDocRef, payload)                    // activeHce = shouldActivate
if (shouldActivate) existingActiveIds.forEach {
    batch.update(cardsRef.document(it), mapOf("activeHce" to false, ...))
}
batch.commit().await()
```
repository 增加 `createCardAndActivate(...)`;规则 create 分支不变,update 分支已覆盖 activeHce-only diff。

---

## P2 NFC 健壮性

### M4 — HCE 冷缓存应答竞态 🟡

**设计**:单飞 + 有界阻塞:
```kotlin
private val coldLoadMutex = Mutex()
// processCommandApdu 冷缓存路径:
val loaded = runBlocking {
    coldLoadMutex.withLock {
        HceRuntimeBus.getCachedActiveCard() ?: withTimeoutOrNull(400) {
            localStore.readActiveHceCard().also { HceRuntimeBus.updateCachedActiveCard(it) }
        }
    }
}
return handleApduWithTelemetry(commandApdu, loaded, HceRuntimeBus.isVaultUnlocked())
```
- 首个 APDU 在 binder 线程短暂阻塞(≤400ms,DataStore 首读通常 <50ms),后续全部走缓存,杜绝乱序 `sendResponseApdu`。
- 超时 → 返回 6A82(读卡器会重试,届时缓存已热)。
- `onCreate` 预热保留。

### M5 — AID 路由单一事实源 🟡

```kotlin
object HceAids {
    val DEFAULT = NfcTagParser.DEFAULT_HCE_AID
    val BUILTIN = setOf(DEFAULT, "F0010203040506", "A0000002471001", "D2760000850101")
}
```
- `syncDynamicAid` 注册 `BUILTIN + 卡 AID`(去重)。
- `apduservice.xml` 静态列表与 `HceAids.BUILTIN` 保持一致(加注释互相引用;测试断言两处集合相等)。
- `evaluateCommandApdu` 的接受集合改用 `HceAids.BUILTIN`,删除私有副本。
- 注:`D2760000850101` 是 NDEF 标准 AID,占用它可能与系统 NDEF 路由冲突——**建议 owner 确认是否保留**;默认方案保留以兼容现有演示读卡器。

### M9 — 静态重放模拟的能力边界显式化 🟢

- `NfcScanScreen` 保存卡时、`CardDetailScreen` HCE 开关旁增加说明文案:"HCE 模拟为固定应答重放,适用于静态校验读卡器;不支持挑战-响应/加密认证类门禁"。
- `GET DATA` 分支:P1/P2 非 0 时返回 6A88(参考数据未找到),而非无条件吐全量 payload。
- README/威胁模型文档记录该边界与 `requireDeviceUnlock=true`、`requireVaultUnlocked` 两层开关语义。

---

## P3 工程化

### L2 — 规则测试可运行 + CI 🟢

1. 仓库根新增 `package.json`:
```json
{
  "private": true,
  "scripts": {
    "test:rules": "firebase emulators:exec --only firestore --project demo-cardwallet \"node --test firestore.test.js\""
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "^4.0.1",
    "firebase-tools": "^14.0.0"
  }
}
```
(`firestore.test.js` 若用 `node:test` 需确认与 rules-unit-testing 的初始化时序兼容,必要时改 mocha/jest。)
2. GitHub Actions `.github/workflows/ci.yml`:
   - job `android`:setup-java 21 → `./gradlew testDebugUnitTest assembleDebug`(缓存 gradle)
   - job `rules`:setup-node 22 → `npm ci` → `npm run test:rules`(需 Java 21 供模拟器)
3. 新增测试清单(见 §4)。

### App Check(需 owner 在 Firebase 控制台操作)

- 启用 Play Integrity(生产)+ Debug provider(开发);恢复 gradle 中 `firebase-appcheck` 依赖并在 `MainActivity.onCreate` 安装。
- 规则侧可选:`request.auth.token.firebase.sign_in_provider` 校验之外,App Check 由 SDK 层强制。
- **阻塞项**:需要真实 Firebase 项目权限,owner 决策。

### Firebase 缺失 fail-fast(打包审查遗留)

`VaultAppRoot` 入口:
```kotlin
if (FirebaseApp.getApps(LocalContext.current).isEmpty()) {
    ConfigMissingScreen()  // 友好提示"缺少 Firebase 配置",不再抛 IllegalStateException 崩溃
    return
}
```
配合文档:本地构建需注入 `google-services.json`(AI Studio 或 owner 提供)。

### namespace 重命名(发布前 chore)

`com.example` → `com.aistudio.nfcshield`(与 applicationId 前缀一致):移动源码目录、改 package 声明与 import、`R` 引用不变(namespace 决定 R 包名,需全量替换 `com.example.R`)。独立 PR,纯机械改动 + 全量测试验证。

### 威胁模型文档(新增 docs/threat-model.md)

明确写出:
- 云端泄露场景下 6 位 PIN 可离线爆破(10⁶ × PBKDF2-120k),缓解 = 迭代数下限 + 参数绑定 + 可选设备密钥绑定(牺牲纯 PIN 跨设备恢复,**列为 owner 可选增强,不在本期**)。
- HCE 静态重放边界、锁屏两层开关、生物识别保障级别矩阵。
- v1 数据淘汰时间表。

---

## 4. 测试计划(新增/扩展)

| 测试 | 覆盖 | 类型 |
|---|---|---|
| KDF 跨算法迁移(SHA1 v1 → SHA256 v2) | H1 | Robolectric |
| verifier v2 拒绝被篡改的 iterations/algorithm | H4 | JVM |
| v2 密文跨 uid 移植解密失败;无盲目回退 | H5 | JVM |
| v1 卡批量迁移幂等、失败不阻塞 | H5 | Robolectric |
| AES_AUTH_BOUND 封装/解封;旧记录升级 | H2 | Robolectric |
| TOGGLE_OFF 后快照重发射不复活 HCE | H6 | Robolectric |
| seal 抛异常时解锁成功、改 PIN 回调必达 | M10 | JVM(Fake store) |
| 持久化锁定跨进程重启生效、退避档位 | M1 | Robolectric |
| increment 规则分支(+1 放行,+2 拒绝) | M2 | rules-unit-testing |
| create+去激活单 batch | M3 | rules + 集成 |
| 冷缓存并发 APDU 单飞、超时 6A82 | M4 | Robolectric |
| 静态/动态 AID 集合一致性 | M5 | JVM |
| 登出后偏好重置 | M11 | Robolectric |
| 日志无 email/uid 明文(断言输出 JSON 字段集) | H3 | JVM |
| notes 超长前置拦截 | L1 | JVM |
| 规则全量回归(现有 firestore.test.js 跑通) | L2 | CI |

---

## 5. 风险与回滚

- **P1 双格式规则**是最大风险面:上线顺序 = 先部署兼容规则 → 再发客户端 v2 → 观察迁移完成率 → 最后收紧规则。任一步可独立回滚。
- 迁移写失败(网络/权限)不得阻塞解锁主流程;所有迁移 `runCatching` + 下次解锁重试。
- H2 新密钥 alias 与旧 alias 并存,旧记录读取路径保留一个版本周期。
- P0 全部为本地改动,可单独成 PR 先行合入。

## 6. 需 owner 决策的事项

1. `D2760000850101`(NDEF AID)是否继续占用(M5)。
2. ~~App Check 启用~~ —— **2026-11-20 已强制执行(ENFORCED)**,见 delivery.md §3。
3. ~~设备密钥绑定 KEK~~ —— **2026-11-20 已实施(schema v3)**,见 delivery.md §7。
4. ~~v1 数据淘汰时间表~~ —— **2026-11-20 已完成**(线上审计 100% v2),见 delivery.md §8。
5. 正式发布签名密钥(当前 release APK 为本地生成密钥,仅本地分发有效)。

---

## 7. 实施闭环记录(2026-11-20)

| 项 | 状态 | 实现要点 | 验证 |
|---|---|---|---|
| H1 KDF 显式化 | ✅ | `kdfAlgorithm` 字段、v1 只读迁移、无 SHA-256 时拒绝创建 | 单测:v2 生成/v1 SHA256/v1 SHA1 解锁+升级标记 |
| H4 参数完整性 | ✅ | verifier v2 绑定 salt+iterations+algorithm;客户端 ≥120k;规则 ≥120k+算法白名单 | 单测:降级/换算法拒绝;规则测试:降级写入拒绝 |
| H5 密文版本化 | ✅ | 卡文档 `cryptoVersion`、按版本选 AAD、无回退、解锁后批量迁移 | 单测:v2 冒充 v1 失败/未知版本失败/v1→v2 迁移 |
| H6 暂停复活 | ✅ | `sealActiveHceCard` 与 quick 标志职责分离;差异同步;quick 缺省 false | 单测:resync 后仍暂停;显式激活仍可用 |
| M10 封装崩溃 | ✅ | `trySealForBiometric` runCatching + `changeVaultPin` 全分支回调 | 代码审查(build 通过);单测覆盖封装失败降级语义由 runCatching 保证 |
| M12 主线程 | ✅ | seal 移入 `Dispatchers.Default` | 同上 |
| M11 偏好残留 | ✅ | 登出清空设备偏好与锁定计数 | 单测:登出后恢复默认 |
| M1 锁定持久化 | ✅ | DataStore 持久化计数 + 5/10/15 次 → 30s/5min/30min | 单测:退避档位 |
| M2 原子计数 | ✅ | `FieldValue.increment(1)` + 规则仅接受 +1 | 规则测试:+1 通过/+2 拒绝 |
| M3 原子激活 | ✅ | `createCardAndActivate` 单 batch | 规则测试(create+update 路径) |
| M4 冷缓存 | ✅ | Mutex 单飞 + 400ms 有界阻塞 | 代码审查;多 APDU 顺序性由单飞保证 |
| M5 AID 一致 | ✅ | `HceAids` 单一事实源;动态注册含内建集 | 单测:XML 集合相等、注册集合 |
| M9 HCE 边界 | ✅ | GET DATA 非 0 tag → 6A88;能力边界文档化 | 单测:6A88;威胁模型 §T4 |
| H2 认证绑定降级 | ✅ | 组合认证器 RSA v3(实施期修正,见方案注);UI 保障级别 | 单测:选择逻辑经代码审查;Robolectric 无法覆盖真实 Keystore |
| H3 日志 PII | ✅ | 只记 uidHash/错误码/路径 | 代码审查 |
| L1 备注上限 | ✅ | UI 1500 字 + ViewModel 校验 + 密文尺寸预检 | 单测:密文尺寸/备注校验(经 ViewModel 层) |
| L2 规则测试 | ✅ | package.json + 本地可运行 + CI 工作流 | 本次执行 8/8 通过 |
| M7 onCleared | ✅ | DEK 清零 | 代码审查 |
| M8 时钟 | ✅ | elapsedRealtime | 单测:策略函数 |
| namespace | ✅ | com.example → com.aistudio.nfcshield | clean 构建 + 全量单测 |
| Firebase fail-fast | ✅ | 缺配置时友好提示屏而非崩溃 | 编译验证 |
| 威胁模型 | ✅ | docs/threat-model.md | 文档 |

**owner 阻塞项(未实施)**:正式签名密钥、真实 google-services.json(均已由 owner 提供并接入)。

## 8. 后续实施(2026-11-20)

| 项 | 结果 |
|---|---|
| App Check 强制执行 | ✅ Firestore 服务 `enforcementMode = ENFORCED`(REST PATCH 于 service 资源,已验证);debug 包需注册调试令牌 |
| 设备密钥绑定 KEK | ✅ vault schema v3:内层 PIN 包裹载荷 + verifier 由 AndroidKeyStore AES-256-GCM 封装;云端 doc 无法离线校验 PIN;v2 解锁后自动升级;无纯 PIN 跨设备恢复 |
| v1 数据淘汰 | ✅ 线上审计 vault 1 条(v2)、卡片 1 条(v2)即 100%;客户端移除 SHA-1/verifier v1/无 AAD 读取与迁移;规则要求 `cryptoVersion == 2`、拒绝 schema v1 |
| 验证 | 单元测试 62/62;规则测试 11/11;release APK 重签构建;`check-delivery.sh` 33 项全绿 |

# CardWallet 交付与运维手册

> 2026-11-20 完整交付记录。配套文档:[修复方案](remediation-design.md) · [威胁模型](threat-model.md)

## 1. Firebase 资源

| 项 | 值 |
|---|---|
| 项目 ID | `gen-lang-client-0191001573`(Default Gemini Project) |
| 项目编号 | `1047859787025` |
| Firestore 数据库 | `ai-studio-android-nfc-32d62da3-0da8-4cb7-8a7a-fcd05f9d38b5` |
| Android 应用 ID | `1:1047859787025:android:57bcae31cb3aa5097ec8b0` |
| 包名 | `com.aistudio.nfcshield.kvnxqr` |
| 登录账号 | sunnywalden0209@gmail.com(Firebase CLI 已登录) |

- `app/google-services.json` 已提交入库,克隆即可构建完整功能版本。
- **Firestore 规则 v2 已部署**(2026-11-20):KDF 参数下限、cryptoVersion 单向迁移、计数器 +1 原子性。

## 2. 签名与构建

| 项 | 值 |
|---|---|
| Upload keystore | `~/.cardwallet/keys/cardwallet-upload.jks`(RSA-4096,有效期至 2056,权限 600) |
| 密码托管 | macOS Keychain,service `com.cardwallet.upload-keystore`(accounts: store-password / key-password) |
| 别名 | `upload` |
| 证书 SHA-256 | `f3c3d1c0af4cad40ac480ac9aa63c7c0aad86a2cd981add282c8c3a9ad3b4527` |
| 证书 SHA-1 | `23dbae9b510d14f2ea0bd495c2d41213670775dd` |

**构建正式签名 APK:**
```bash
./scripts/build-release.sh
```
密码从 Keychain 读取,不落盘。产物:`app/build/outputs/apk/release/app-release.apk`。

> ⚠️ keystore 是应用身份的唯一凭证,丢失将无法更新应用。建议另行离线备份(加密存储)。
> 上架 Google Play 时建议启用 Play App Signing,将此 key 作为 upload key 托管给 Play。

## 3. App Check

- **Play Integrity 已配置**:release 证书 SHA-256(`f3c3d1c0…4527`)已注册到应用;`minDeviceRecognitionLevel = MEETS_DEVICE_INTEGRITY`(拒绝 root/篡改设备)。
- 代码接入:release 用 `PlayIntegrityAppCheckProviderFactory`,debug 用 `DebugAppCheckProviderFactory`(分 source set)。
- **强制执行已开启(ENFORCED,2026-11-20 15:58 UTC,已验证)**:
  - 服务:`services/firestore.googleapis.com` → `enforcementMode = ENFORCED`。
  - 生效 API 为 **service 资源本身的 PATCH**;公开 App Check REST API 没有
    `…/appCheckConfig` 子资源(该路径 404,v1Beta 文档中的示例 curl 不可用)。
  - 查询/复核:
    ```bash
    curl -H "Authorization: Bearer <token>" \
      "https://firebaseappcheck.googleapis.com/v1/projects/gen-lang-client-0191001573/services/firestore.googleapis.com"
    ```
  - 关闭(应急回滚):
    ```bash
    curl -X PATCH \
      "https://firebaseappcheck.googleapis.com/v1/projects/gen-lang-client-0191001573/services/firestore.googleapis.com?updateMask=enforcementMode" \
      -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
      -d '{"enforcementMode": "UNENFORCED"}'
    ```
- **强制执行后的影响**:
  - release 构建(Play Integrity)正常访问。
  - **debug 构建**必须在 Firebase Console → App Check → 应用 → 管理调试令牌 注册
    logcat 首次打印的 debug token,否则 Firestore 请求全部被拒。
  - AI Studio Web 预览等非 Android 客户端不再能访问 Firestore(唯一受影响的既有场景,已接受)。

## 4. Google 登录

- Web OAuth 客户端(`default_web_client_id`)已由 google-services.json 提供。
- **正式签名 SHA-1 需注册到 Android OAuth 客户端**(Cloud Console → API 和服务 → 凭据 → Android 客户端),否则正式包 Google 登录报 DEVELOPER_ERROR。
  - 待注册 SHA-1:`23dbae9b510d14f2ea0bd495c2d41213670775dd`
  - 包名:`com.aistudio.nfcshield.kvnxqr`

## 5. 已交付验证

- 单元测试 62/62、Firestore 规则测试 11/11(emulator,2026-11-20)
- CI 双 job(Android / rules)全绿,PR #1、#2 已合并 main
- release APK 正式签名 + Firebase 完整配置 + App Check 接入且**已强制执行**

## 6. AI Studio 同步防回退

AI Studio 重新生成代码会覆盖本地修复(已发生两次)。每次从 AI Studio 导出/同步后,先运行:

```bash
./scripts/check-delivery.sh          # 33 项静态检查(秒级)
./scripts/check-delivery.sh --build  # 追加单元测试
```

检查覆盖:google-services.json、动态资源查找、App Check 接入、包名、可执行位、
加密核心(KDF/verifier v2/版本化)、规则关键条款、CI/文档。失败时输出对应修复指引。

## 7. 保险库 schema v3(设备密钥绑定,2026-11-20 已交付)

**设计**:PIN 包裹的 DEK 与 verifier v2 不再直接写入云端,而是先序列化为内部 JSON,
再用 AndroidKeyStore 中不可导出的 AES-256-GCM 设备密钥(`shield_nfc_device_wrap_key_v1`)
整体封装。云端 `vault/config` 只保存外层密文 + 公开 KDF 参数 + 占位 verifier
(`device-bound:v3`)。

- **收益**:仅凭云端文档 + PIN 无法做任何离线校验/爆破(T1 残余风险消除)。
- **代价(已确认接受)**:**不存在纯 PIN 的跨设备恢复**。更换设备、卸载应用、清除应用数据
  或恢复出厂设置都会删除设备密钥,届时云端数据不可再解密。请勿卸载应用。
- **自动迁移**:v2 保险库在下次 PIN 解锁成功后自动重封装为 v3 并同步;解锁后 UI 会提示
  “保险库已升级为设备密钥绑定”。规则只允许 `schemaVersion` 前进,禁止 v3→v2 回退。
- **失败语义**:设备密钥缺失 → “本设备无法解锁”;设备密钥存在但认证失败 → “密钥已变更或数据受损”;
  两者都不会消耗 PIN 失败次数。
- **版本约束**:升级为 v3 后,旧版 APK(schema ≤2 客户端)无法解锁该保险库,请保持新版本。
  设备密钥别名 `shield_nfc_device_wrap_key_v1` 在登出时**不会**删除(否则数据永久不可解锁),
  仅卸载应用/清除数据/恢复出厂会使其失效。

## 8. v1 数据淘汰(2026-11-20 已完成)

- **先决条件审计(线上 Firestore,2026-11-20)**:vault config 仅 1 条(schema v2 / SHA-256 / 120k),
  卡片仅 1 条(cryptoVersion=2)→ **迁移率 100%**。
- 客户端已移除:vault schema v1 解锁路径(SHA-1 / verifier v1)、卡密文 v1 读取与批量迁移。
- 规则已收紧:卡文档必须显式 `cryptoVersion == 2`;v1 文档连 `activeHce` 切换也不可写;
  vault 写入仅接受 v2(迁移来源)或 v3,v1 被拒绝。
- 本地 `AES_KEYSTORE` 生物缓存读取路径保留(仅升级用途,不涉及云端数据淘汰)。
## 9. 云端同步容灾与 App Check 调试令牌(2026-11-20 已交付)

### 9.1 本地加密离线缓存

- `LocalVaultStore` 新增按账号隔离的加密缓存:与 Firestore 完全一致的 `VaultConfigDoc`
  (**含 v3 `deviceBinding`**)与卡片密文列表;不落盘任何明文 DEK/PIN/卡片明文。
- 启动/锁屏唤醒时先水合缓存 → 有缓存直接进入 PIN/生物识别解锁与卡包;云端监听在后台
  重连。缓存写入失败不影响正常同步;空卡片快照**不会**清空已有缓存。
- 退出登录/切换账号时缓存随 `clearSensitiveLocalSession()` 一并清除。

### 9.2 令牌预热、阶梯退避与自动恢复

- Auth ID Token 与 App Check Token 刷新超时 4s → **10s**(覆盖 Doze 唤醒与 Play Integrity 往返)。
- Firestore 监听对 `PERMISSION_DENIED / UNAVAILABLE / DEADLINE_EXCEEDED / ABORTED`
  阶梯退避重试 **1s → 2s → 4s → 8s**,每次重试前强制刷新双令牌;耗尽后保留缓存并等待
  前台恢复或网络恢复自动重连(`NetworkCallback` + `ON_START`)。
- 仓储层 `refreshSessionCredentials` 返回 `CredentialRefreshResult`(Auth / App Check / 网络
  三维分类),ViewModel 暴露 `cloudSyncIssue: StateFlow<SyncDiagnosticReport?>`。

### 9.3 诊断界面

- **无缓存 + 同步受限**:锁屏错误页展示 `SyncDiagnosticsCard`(Google 登录会话 / App Check
  设备证明 / 网络三项探针 + Firestore 错误码),重试按钮带加载态。
- **有缓存 + 云端受限**:首页顶部显示琥珀色离线横幅,可一键重试或打开诊断弹窗;恢复后自动收起。
- **Debug 构建专属**:诊断卡展示当前生效的 App Check Debug Token 与来源
  (启动参数 / Secrets / 本机随机),支持一键复制到 Firebase Console。Release 构建中
  `activeDebugTokenInfo()` 恒为 null,APK 内不包含任何调试令牌。

### 9.4 Debug Token 注入

| 来源(优先级) | 说明 |
|---|---|
| Intent extra | `appcheck_debug_token` / `firebase_app_check_debug_token`(调试启动参数) |
| AI Studio Secrets | `.env` 的 `FIREBASE_APPCHECK_DEBUG_TOKEN`,仅注入 debug 变体 |
| SDK 随机生成 | 未配置前两者的兜底;logcat 打印,需在 Console 注册 |

安装器在安装 Debug Provider 前以 `commit()` 同步写入 SDK 的调试存储
(`com.google.firebase.appcheck.debug.store.<persistenceKey>`),确保首次取证即使用已注册令牌。

**上线后操作**:将已在 Firebase Console 注册的调试令牌填入 AI Studio Secrets 面板
(键名 `FIREBASE_APPCHECK_DEBUG_TOKEN`),Debug 构建与云端模拟器即可在 `ENFORCED` 下正常同步。

**当前状态(2026-11-20)**:已通过 App Check Admin API 注册调试令牌
`ShieldVault debug (2026-11-20)`(资源位于该 Android 应用的 debugTokens 下)。
令牌值仅保存在本机 `.env`(权限 600,**不入库**)并已同步注入 debug BuildConfig;
Release 构建仍为空。若需 AI Studio 云端模拟器通过校验,请将 `.env` 中的
`FIREBASE_APPCHECK_DEBUG_TOKEN` 值粘贴到 AI Studio Secrets 面板(同名键)。

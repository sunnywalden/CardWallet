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

- **Play Integrity 已配置**:release 证书 SHA-256 已注册到应用;`minDeviceRecognitionLevel = MEETS_DEVICE_INTEGRITY`(拒绝 root/篡改设备)。
- 代码接入:release 用 `PlayIntegrityAppCheckProviderFactory`,debug 用 `DebugAppCheckProviderFactory`(分 source set)。
- **强制执行当前为关闭**(UNENFORCED)。原因:避免立即阻断 AI Studio Web 预览等无 App Check 的客户端。

**启用强制执行(验证正式版运行正常后):**
```bash
# 方式一:Firebase Console → App Check → Firestore → 启用强制执行
# 方式二:API
curl -X PATCH \
  "https://firebaseappcheck.googleapis.com/v1beta/projects/gen-lang-client-0191001573/services/firestore.googleapis.com/appCheckConfig?updateMask=enforcementMode" \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{"enforcementMode": "ENFORCED"}'
```

**Debug 构建**:首次运行会在 logcat 打印 debug token,需在 Console → App Check → 应用 → 管理调试令牌 中注册,否则 debug 包在强制执行后无法访问 Firestore。

## 4. Google 登录

- Web OAuth 客户端(`default_web_client_id`)已由 google-services.json 提供。
- **正式签名 SHA-1 需注册到 Android OAuth 客户端**(Cloud Console → API 和服务 → 凭据 → Android 客户端),否则正式包 Google 登录报 DEVELOPER_ERROR。
  - 待注册 SHA-1:`23dbae9b510d14f2ea0bd495c2d41213670775dd`
  - 包名:`com.aistudio.nfcshield.kvnxqr`

## 5. 已交付验证

- 单元测试 26/26、Firestore 规则测试 8/8(emulator)
- CI 双 job(Android / rules)全绿,PR #1、#2 已合并 main
- release APK 正式签名 + Firebase 完整配置 + App Check 接入

## 6. AI Studio 同步防回退

AI Studio 重新生成代码会覆盖本地修复(已发生两次)。每次从 AI Studio 导出/同步后,先运行:

```bash
./scripts/check-delivery.sh          # 21 项静态检查(秒级)
./scripts/check-delivery.sh --build  # 追加单元测试
```

检查覆盖:google-services.json、动态资源查找、App Check 接入、包名、可执行位、
加密核心(KDF/verifier v2/版本化)、规则关键条款、CI/文档。失败时输出对应修复指引。

## 7. 剩余可选增强(需产品决策)

- KEK 设备密钥绑定:消除云端文档离线 PIN 爆破,代价是失去纯 PIN 跨设备恢复(见威胁模型 §T1)。
- v1 数据淘汰:两个版本周期后移除 v1 读取路径(先确认迁移率 100%)。
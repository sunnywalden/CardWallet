# CardWallet(盾匙 NFC)威胁模型

> 依据 docs/remediation-design.md 的实施结果整理。适用于 schema v2 及之后的数据格式。

## 1. 资产与信任边界

| 资产 | 位置 | 保护 |
|---|---|---|
| DEK(256-bit 数据密钥) | 内存 / PIN 包裹后云端 / 硬件密钥封装后本地 | PBKDF2-SHA256(120k) 包裹;RSA-OAEP 认证绑定封装 |
| 卡密文(UID/APDU/备注) | Firestore,`users/{uid}/cards` | AES-256-GCM + owner AAD(v2) |
| 包裹后的 DEK 与 PIN verifier | Firestore,`users/{uid}/vault/config` | 零知识:云端永不见 DEK/PIN 明文 |
| HCE 激活卡配置 | 本地 DataStore(密文) | AndroidKeyStore AES-GCM 设备绑定 |
| Google 身份 | Firebase Auth | 仅用于路径隔离与规则鉴权 |

**信任边界**:Android 系统 Keystore(硬件安全)、Firebase(存储但不解密)。PIN、DEK、卡明文从不离开设备。

## 2. 威胁与缓解

### T1 云端数据泄露 / 备份外泄
攻击者取得 vault config 文档后,可能尝试在离线状态对 6 位 PIN(10⁶ 空间)做 PBKDF2 暴力搜索。

- 缓解:KDF 迭代下限 120k(客户端硬校验 + 规则强制)、verifier v2 绑定 salt/iterations/algorithm(篡改即拒)、参数降级拒绝。
- **设备密钥绑定(schema v3,2026-11-20 已实施)**:PIN 包裹的 DEK 与 verifier 均被封装在
  设备硬件密钥(AndroidKeyStore,不可导出 AES-256-GCM)之下。云端文档**没有任何可离线校验
  PIN 猜想的材料**——KDF 参数是公开的,但密文无法在缺少设备密钥时打开。
- 残余风险:攻击者若同时控制设备(可调用 Keystore)或取得设备上的密钥,仍可做在线/本地爆破;
  6 位 PIN 的空间很小,本应用不承诺抗有预算的针对性爆破。
- 代价:失去纯 PIN 跨设备恢复;卸载/清除数据/换机会丢失设备密钥(见 §4)。

### T2 恶意/被盗会话(token 泄露)
攻击者可读写该用户路径。

- 缓解:规则字段白名单、尺寸/枚举校验、`createdAt`/`userId` 不可变、`cryptoVersion` 只能前进、`emulationCount` 只能 +1。
- 无法缓解:会话持有者可删除数据、写入垃圾密文(完整性由 GCM 保证,无法伪造可解密的恶意内容)。

### T3 设备丢失
- 生物识别封装为 RSA 私钥 `setUserAuthenticationRequired(true)` + 每次使用认证 + 生物特征变更失效;组合认证路径要求生物或锁屏凭据。
- 旧版 AES_KEYSTORE 封装(无认证绑定)仅存在于升级前安装,下次 PIN 解锁自动替换为认证绑定;UI 明示。
- HCE 静态 AID 服务 `requireDeviceUnlock=true`,锁屏状态下不可刷。
- `allowBackup=false`:应用数据不进入云备份与设备迁移。

### T4 读卡器侧攻击(HCE 重放)
HCE 为**固定应答重放**:对同一 AID 的 SELECT/READ BINARY/GET DATA 返回预录载荷。

- 可被任意读卡器读取(与真实门禁卡相同的暴露面)。
- 不支持挑战-响应/加密认证读卡器(此类门禁无法模拟,UI 已明示)。
- 缓解:卡槽可由快捷开关随时暂停;`requireVaultUnlocked` 可要求解锁后才应答(默认关闭,用户可在安全设置开启)。

### T5 跨用户数据移植
v2 密文以 `shield-nfc:card:v2:{uid}` 为 AAD,移植到其他 uid 下 GCM 认证失败;按 `cryptoVersion` 精确选择 AAD,**不存在无 AAD 的盲目回退**。

### T6 生物特征变更 / 密钥失效
`setInvalidatedByBiometricEnrollment(true)`:录入新生物特征后硬密钥永久失效 → 应用清除封装并引导 PIN 重绑(PIN 包裹的 DEK 不受影响)。

### T7 日志与诊断泄露
错误日志只含 `uidHash`(SHA-256 前 12 hex)、错误码、操作类型与路径;debug 下才含 error message;不记录 email/provider 信息。

## 3. 明确不在范围内

- Root/自定义 ROM 环境的 KeyStore 可信性。
- 屏幕录制/无障碍服务导致的 PIN 侧信道。
- Firebase 控制台管理员的规则变更。
- 物理读卡器的电磁侧信道。

## 4. 数据版本与淘汰

| 版本 | 状态(2026-11-20) |
|---|---|
| vault schema v1(SHA-1 / verifier v1) | **已淘汰**:客户端与规则均拒绝读取/写入(线上审计 100% v2) |
| vault schema v2(明文 PIN 包裹) | 只读,解锁成功后自动升级为 v3(设备密钥绑定) |
| vault schema v3(设备密钥绑定) | 当前写入格式;`schemaVersion` 只允许前进 |
| 卡密文 v1(无 AAD) | **已淘汰**:读取与迁移代码已移除,规则要求 `cryptoVersion == 2` |
| 卡密文 v2(owner AAD) | 唯一有效格式 |
| `AES_KEYSTORE` 本地封装 | 只读(仅本机升级用途),下次 PIN 解锁替换为认证绑定 RSA |

**v3 恢复语义**:设备密钥仅存在于本机 AndroidKeyStore。卸载应用、清除应用数据、恢复出厂设置
或更换设备后,云端数据不可再解密。这是消除云端离线 PIN 爆破的既定代价,已在交付文档中明示。

## 5. 待 owner 决策的增强

1. ~~设备密钥绑定 KEK~~ —— 已于 2026-11-20 实施(schema v3)。
2. ~~App Check(Play Integrity)启用~~ —— 已于 2026-11-20 强制(ENFORCED)。
3. NDEF 标准 AID(`D2760000850101`)是否继续占用(仍未决)。
## 6. 离线缓存与调试令牌(2026-11-20 新增)

### T8 本地加密缓存
- 本地 DataStore 仅保存与云端完全一致的密文(vault config 含 v3 deviceBinding + 卡片密文),
  按账号隔离;解锁仍需 PIN 或认证绑定生物识别解出 DEK。
- 登出/切换账号即清除;空快照不会覆盖已有缓存。
- 残余风险:已解锁进程内的代码可调用 Keystore 解封(与在线状态相同的信任边界);
  设备密钥仅在本机,缓存被拷走也无法在别的设备解密。

### T9 调试令牌
- App Check Debug Token 仅存在于 **debug 构建**:AI Studio Secrets / 启动 Intent 注入,
  安装前同步写入 SDK 调试存储;Release 变体 BuildConfig 恒为空且 `activeDebugTokenInfo()` 返回 null。
- 诊断 UI 在 debug 下展示并允许复制该令牌(用途即注册到 Console);日志只记录来源与长度,不打印令牌。
- 令牌是临时凭证:调试结束后应在 Firebase Console / CLI 撤销;禁止提交到源码或分享。

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
攻击者取得 vault config 文档后,可在离线状态对 6 位 PIN(10⁶ 空间)做 PBKDF2 暴力搜索。

- 缓解:KDF 迭代下限 120k(客户端硬校验 + 规则强制)、verifier v2 绑定 salt/iterations/algorithm(篡改即拒)、参数降级拒绝。
- 残余风险:10⁶ 仍可被 GPU 集群在可接受时间内穷举。**可选增强**(未实施,需 owner 决策):将 KEK 再与设备密钥绑定,使云端文档单独不可用——代价是失去纯 PIN 跨设备恢复。
- 结论:本应用定位"防云端拖库直接读取",不承诺"抗有预算的针对性 PIN 爆破"。

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

| 版本 | 状态 |
|---|---|
| vault schema v1(SHA-1 可能、verifier v1) | 只读,解锁成功后自动升级为 v2 |
| 卡密文 v1(无 AAD) | 只读,解锁后自动重加密为 v2 |
| `AES_KEYSTORE` 本地封装 | 只读,下次 PIN 解锁替换为认证绑定 RSA |
| 写入路径 | 一律 v2;规则拒绝新写入 v1 |

淘汰里程碑建议:上线两个版本周期后,在客户端与规则中同时移除 v1 读取路径(需确认迁移率 100%,由 owner 决定具体时点)。

## 5. 待 owner 决策的增强

1. 设备密钥绑定 KEK(T1 可选增强)。
2. App Check(Play Integrity)启用——需 Firebase 项目权限。
3. NDEF 标准 AID(`D2760000850101`)是否继续占用。
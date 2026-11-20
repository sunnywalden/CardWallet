package com.aistudio.nfcshield.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.aistudio.nfcshield.data.model.NfcTechType
import java.nio.charset.Charset
import java.util.Locale

data class NfcTechDebugEntry(
    val shortName: String,
    val fullClassName: String,
    val details: List<Pair<String, String>>,
    val probeError: String? = null
)

data class ScannedNfcTagInfo(
    val uidHex: String,
    val maskedUid: String,
    val primaryTech: NfcTechType,
    val techList: List<String>,
    val atqaHex: String,
    val sakHex: String,
    val historicalBytesHex: String,
    val aidHex: String,
    val ndefSummary: String,
    val mifareSummary: String,
    val hceSupported: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
    val uidLengthBytes: Int = 0,
    val rawTechList: List<String> = emptyList(),
    val techEntries: List<NfcTechDebugEntry> = emptyList(),
    val diagnosticSummary: String = "",
    val captureSource: String = "ReaderMode"
)

object NfcTagParser {
    const val DEFAULT_HCE_AID = "F04E464353484945"

    fun parseTag(tag: Tag, captureSource: String = "ReaderMode"): ScannedNfcTagInfo {
        val uidBytes = tag.id ?: ByteArray(0)
        val uidHex = bytesToColonHex(uidBytes)
        val maskedUid = buildMaskedUid(uidBytes)
        val rawTechs = tag.techList?.toList().orEmpty()
        val readableTechs = rawTechs.map { it.substringAfterLast('.') }

        var atqaHex = ""
        var sakHex = ""
        var historicalBytesHex = ""
        var detectedAidHex = ""
        var ndefSummary = ""
        var mifareSummary = ""
        val techEntries = mutableListOf<NfcTechDebugEntry>()

        rawTechs.forEach { fullClass ->
            val shortName = fullClass.substringAfterLast('.')
            val details = mutableListOf<Pair<String, String>>()
            var errorMsg: String? = null

            when (fullClass) {
                NfcA::class.java.name -> {
                    val nfcA = NfcA.get(tag)
                    if (nfcA != null) {
                        runCatching {
                            atqaHex = bytesToRawHex(nfcA.atqa)
                            sakHex = "%02X".format(nfcA.sak.toInt() and 0xFF)
                            details += "ATQA" to atqaHex.ifEmpty { "-" }
                            details += "SAK" to "0x$sakHex"
                            details += "MaxTransceive" to "${nfcA.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                NfcB::class.java.name -> {
                    val nfcB = NfcB.get(tag)
                    if (nfcB != null) {
                        runCatching {
                            val appData = bytesToRawHex(nfcB.applicationData)
                            val protoInfo = bytesToRawHex(nfcB.protocolInfo)
                            if (atqaHex.isEmpty()) atqaHex = appData
                            if (sakHex.isEmpty()) sakHex = protoInfo
                            details += "ApplicationData" to appData.ifEmpty { "-" }
                            details += "ProtocolInfo" to protoInfo.ifEmpty { "-" }
                            details += "MaxTransceive" to "${nfcB.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                NfcF::class.java.name -> {
                    val nfcF = NfcF.get(tag)
                    if (nfcF != null) {
                        runCatching {
                            val sysCode = bytesToRawHex(nfcF.systemCode)
                            val pmm = bytesToRawHex(nfcF.manufacturer)
                            if (historicalBytesHex.isEmpty() && sysCode.isNotEmpty()) {
                                historicalBytesHex = "SYS:$sysCode"
                            }
                            details += "SystemCode" to sysCode.ifEmpty { "-" }
                            details += "Manufacturer (PMm)" to pmm.ifEmpty { "-" }
                            details += "MaxTransceive" to "${nfcF.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                NfcV::class.java.name -> {
                    val nfcV = NfcV.get(tag)
                    if (nfcV != null) {
                        runCatching {
                            details += "DSF ID" to "0x%02X".format(nfcV.dsfId.toInt() and 0xFF)
                            details += "ResponseFlags" to "0x%02X".format(nfcV.responseFlags.toInt() and 0xFF)
                            details += "MaxTransceive" to "${nfcV.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                IsoDep::class.java.name -> {
                    val isoDep = IsoDep.get(tag)
                    if (isoDep != null) {
                        runCatching {
                            isoDep.connect()
                            isoDep.timeout = 1500
                            val hist = isoDep.historicalBytes
                            val hiLayer = isoDep.hiLayerResponse
                            val chosenHist = hist ?: hiLayer
                            if (chosenHist != null && chosenHist.isNotEmpty()) {
                                historicalBytesHex = bytesToRawHex(chosenHist)
                            }
                            details += "MaxTransceive" to "${isoDep.maxTransceiveLength} bytes"
                            details += "ExtendedAPDU" to if (isoDep.isExtendedLengthApduSupported) "支持" else "不支持"
                            if (hist != null && hist.isNotEmpty()) {
                                details += "HistoricalBytes" to bytesToRawHex(hist)
                            }
                            if (hiLayer != null && hiLayer.isNotEmpty()) {
                                details += "HiLayerResponse" to bytesToRawHex(hiLayer)
                            }
                            val ndefSelectCmd = hexStringToBytes("00A4040007D276000085010100")
                            val respResult = runCatching { isoDep.transceive(ndefSelectCmd) }
                            val resp = respResult.getOrNull()
                            if (resp != null && resp.size >= 2) {
                                val sw = bytesToRawHex(resp.takeLast(2).toByteArray())
                                details += "SELECT NDEF SW" to sw
                                if (resp[resp.size - 2] == 0x90.toByte() && resp[resp.size - 1] == 0x00.toByte()) {
                                    detectedAidHex = "D2760000850101"
                                }
                            } else if (respResult.isFailure) {
                                details += "SELECT NDEF 探测" to (respResult.exceptionOrNull()?.javaClass?.simpleName ?: "失败")
                            }
                        }.onFailure {
                            errorMsg = it.localizedMessage ?: it.javaClass.simpleName
                        }
                        runCatching { isoDep.close() }
                    }
                }

                MifareClassic::class.java.name -> {
                    val mc = MifareClassic.get(tag)
                    if (mc != null) {
                        runCatching {
                            val typeLabel = when (mc.type) {
                                MifareClassic.TYPE_CLASSIC -> "Classic"
                                MifareClassic.TYPE_PLUS -> "Plus"
                                MifareClassic.TYPE_PRO -> "Pro"
                                else -> "MIFARE"
                            }
                            mifareSummary = "$typeLabel · ${mc.size} 字节 · ${mc.sectorCount} 扇区 / ${mc.blockCount} 块（物理加密扇区受硬件密钥保护）"
                            details += "类型" to typeLabel
                            details += "容量 / 扇区" to "${mc.size} B (${mc.sectorCount} 扇区 / ${mc.blockCount} 块)"
                            details += "MaxTransceive" to "${mc.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                MifareUltralight::class.java.name -> {
                    val mu = MifareUltralight.get(tag)
                    if (mu != null) {
                        runCatching {
                            val typeLabel = when (mu.type) {
                                MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                                MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                                else -> "Ultralight"
                            }
                            mifareSummary = "MIFARE $typeLabel"
                            details += "类型" to typeLabel
                            details += "MaxTransceive" to "${mu.maxTransceiveLength} bytes"
                        }.onFailure { errorMsg = it.localizedMessage ?: it.javaClass.simpleName }
                    }
                }

                Ndef::class.java.name -> {
                    val ndef = Ndef.get(tag)
                    if (ndef != null) {
                        runCatching {
                            details += "NDEF 类型" to ndef.type
                            details += "最大容量" to "${ndef.maxSize} bytes"
                            details += "可写 / 可锁只读" to "${if (ndef.isWritable) "可写" else "只读"} / ${if (ndef.canMakeReadOnly()) "可锁" else "不可锁"}"
                            val msg = ndef.cachedNdefMessage ?: run {
                                ndef.connect()
                                ndef.ndefMessage
                            }
                            if (msg != null) {
                                ndefSummary = parseNdefMessage(msg)
                                details += "NDEF 记录数" to "${msg.records.size}"
                            }
                        }.onFailure {
                            errorMsg = it.localizedMessage ?: it.javaClass.simpleName
                        }
                        runCatching { ndef.close() }
                    }
                }

                else -> {
                    details += "原生类" to fullClass
                }
            }

            techEntries += NfcTechDebugEntry(
                shortName = shortName,
                fullClassName = fullClass,
                details = details,
                probeError = errorMsg
            )
        }

        val primaryTech = when {
            rawTechs.contains(IsoDep::class.java.name) -> NfcTechType.ISO_DEP
            rawTechs.contains(MifareClassic::class.java.name) -> NfcTechType.MIFARE_CLASSIC
            rawTechs.contains(MifareUltralight::class.java.name) -> NfcTechType.MIFARE_ULTRALIGHT
            rawTechs.contains(Ndef::class.java.name) -> NfcTechType.NDEF
            rawTechs.contains(NfcA::class.java.name) -> NfcTechType.NFC_A
            rawTechs.contains(NfcB::class.java.name) -> NfcTechType.NFC_B
            rawTechs.contains(NfcF::class.java.name) -> NfcTechType.NFC_F
            rawTechs.contains(NfcV::class.java.name) -> NfcTechType.NFC_V
            else -> NfcTechType.OTHER
        }

        val hceSupported = primaryTech == NfcTechType.ISO_DEP
        val diagnosticSummary = buildDiagnosticHint(primaryTech, readableTechs, techEntries)

        return ScannedNfcTagInfo(
            uidHex = uidHex,
            maskedUid = maskedUid,
            primaryTech = primaryTech,
            techList = readableTechs,
            atqaHex = atqaHex,
            sakHex = sakHex,
            historicalBytesHex = historicalBytesHex,
            aidHex = detectedAidHex.ifEmpty { if (hceSupported) DEFAULT_HCE_AID else "" },
            ndefSummary = ndefSummary,
            mifareSummary = mifareSummary,
            hceSupported = hceSupported,
            timestampMillis = System.currentTimeMillis(),
            uidLengthBytes = uidBytes.size,
            rawTechList = rawTechs,
            techEntries = techEntries,
            diagnosticSummary = diagnosticSummary,
            captureSource = captureSource
        )
    }

    fun buildDiagnosticHint(
        primaryTech: NfcTechType,
        readableTechs: List<String>,
        techEntries: List<NfcTechDebugEntry>
    ): String {
        val errors = techEntries.mapNotNull { entry -> entry.probeError?.let { "${entry.shortName}: $it" } }
        val baseHint = when (primaryTech) {
            NfcTechType.ISO_DEP -> "检测到 IsoDep (ISO 14443-4) 协议栈，支持 APDU 指令交互；非 Root Android HCE 可模拟自定义 AID 的 ISO-DEP 通信。"
            NfcTechType.MIFARE_CLASSIC -> "检测到 MIFARE Classic 底层标签（常见于传统小区门禁卡）。Android 标准 HCE 不支持模拟 MIFARE 扇区与固定物理 UID。"
            NfcTechType.MIFARE_ULTRALIGHT -> "检测到 MIFARE Ultralight 标签，支持读取 UID 与公开页/NDEF，无法通过非 Root HCE 克隆固定物理 UID。"
            NfcTechType.NFC_F -> "检测到 NFC-F (FeliCa) 协议栈，常见于部分交通卡/日本制式卡片；读取需保持贴卡稳定，加密区受芯片安全保护。"
            NfcTechType.NFC_B -> "检测到 NFC-B (ISO 14443-3B) 协议栈，常见于二代证或特定行业卡，常规 HCE 不支持 Type-B 模拟。"
            NfcTechType.NFC_A -> "仅检测到基础 NFC-A (ISO 14443-3A) 载波层，未暴露 IsoDep APDU 通道。"
            NfcTechType.NFC_V -> "检测到 NFC-V (ISO 15693) 远距离高频标签。"
            NfcTechType.NDEF -> "检测到 NDEF 标准格式标签，可直接读取记录内容。"
            NfcTechType.OTHER -> "捕获到标签技术栈 [${readableTechs.joinToString(", ")}]。"
        }
        return if (errors.isEmpty()) baseHint else "$baseHint\n探测异常: ${errors.joinToString("；")}"
    }

    fun formatDebugReport(
        status: NfcHardwareStatus,
        logs: List<ScannedNfcTagInfo>
    ): String = buildString {
        appendLine("=== 盾匙 NFC 硬件与协议调试报告 ===")
        appendLine("设备: ${status.deviceSummary}")
        appendLine("Manifest NFC 权限声明: ${status.nfcPermissionDeclared}")
        appendLine("NFC 运行时授权 (checkSelfPermission): ${status.nfcPermissionGranted}")
        appendLine("HCE 服务权限 (BIND_NFC_SERVICE): ${status.hceBindServiceDeclared}")
        appendLine("NFC 硬件支持: ${status.nfcSupported} (FEATURE_NFC=${status.nfcFeatureDeclared})")
        appendLine("NFC 适配器状态: ${status.adapterStateLabel} (enabled=${status.nfcEnabled})")
        appendLine("HCE 卡模拟支持: ${status.hceSupported} (HCE-F=${status.hceFSupported})")
        appendLine("HCE 路由模式: ${status.hceSelectionMode} (默认服务=${status.hceDefaultService})")
        appendLine("已注册 AID: ${status.registeredAids.ifEmpty { listOf("无") }.joinToString(", ")}")
        appendLine()
        if (logs.isEmpty()) {
            appendLine("暂无扫描到的 NFC 标签记录。")
        } else {
            logs.forEachIndexed { idx, log ->
                appendLine("--- 标签记录 #${idx + 1} (${log.captureSource}) ---")
                appendLine("UID: ${log.uidHex.ifEmpty { "空" }} (${log.uidLengthBytes} bytes)")
                appendLine("主协议: ${log.primaryTech.displayName}")
                appendLine("技术栈 (Short): ${log.techList.joinToString(", ")}")
                appendLine("技术栈 (Raw): ${log.rawTechList.joinToString(", ")}")
                log.techEntries.forEach { entry ->
                    val kv = entry.details.joinToString(", ") { "${it.first}=${it.second}" }
                    val err = entry.probeError?.let { " | ERROR=$it" }.orEmpty()
                    appendLine("  * ${entry.shortName}: $kv$err")
                }
                appendLine("诊断: ${log.diagnosticSummary}")
                appendLine()
            }
        }
    }

    private fun parseNdefMessage(message: NdefMessage): String {
        val parts = message.records.mapNotNull { record ->
            when {
                record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    decodeTextRecord(record.payload)
                }
                record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                    record.toUri()?.toString()
                }
                record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                    val mime = String(record.type, Charsets.US_ASCII)
                    val body = String(record.payload, Charsets.UTF_8).take(80)
                    "[$mime] $body"
                }
                else -> {
                    if (record.payload.isNotEmpty()) {
                        "RAW(${bytesToRawHex(record.payload.take(16).toByteArray())})"
                    } else null
                }
            }
        }
        return parts.joinToString(" | ")
    }

    private fun decodeTextRecord(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val isUtf16 = (status and 0x80) != 0
        val langLen = status and 0x3F
        if (payload.size <= 1 + langLen) return null
        val charset = if (isUtf16) Charset.forName("UTF-16") else Charsets.UTF_8
        return String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
    }

    fun buildMaskedUid(uidBytes: ByteArray): String {
        if (uidBytes.isEmpty()) return "••••"
        val raw = bytesToRawHex(uidBytes)
        return if (raw.length <= 4) "•••• $raw" else "•••• ${raw.takeLast(4)}"
    }

    fun buildMaskedUidFromHexString(uidHex: String): String {
        val clean = normalizeHex(uidHex)
        if (clean.isEmpty()) return "••••"
        return if (clean.length <= 4) "•••• $clean" else "•••• ${clean.takeLast(4)}"
    }

    fun bytesToColonHex(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    fun bytesToRawHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    fun normalizeHex(input: String): String =
        input.uppercase(Locale.US).replace(Regex("[^0-9A-F]"), "")

    fun isValidAidHex(input: String): Boolean {
        val clean = normalizeHex(input)
        return clean.length in 10..32 && clean.length % 2 == 0
    }

    fun hexStringToBytes(hex: String): ByteArray {
        val clean = normalizeHex(hex)
        if (clean.length % 2 != 0 || clean.isEmpty()) return ByteArray(0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

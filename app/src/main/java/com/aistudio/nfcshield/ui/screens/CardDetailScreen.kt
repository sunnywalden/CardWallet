package com.aistudio.nfcshield.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardIconOption
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.ui.components.AccessCardVisual
import com.aistudio.nfcshield.ui.components.toColor
import com.aistudio.nfcshield.ui.components.toIcon
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultAmberContainer
import com.aistudio.nfcshield.ui.theme.VaultBorder
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultCyanContainer
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(
    card: DecryptedAccessCard,
    onBack: () -> Unit,
    onToggleHce: (DecryptedAccessCard) -> Unit,
    onUpdateCard: (
        existing: DecryptedAccessCard,
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        secrets: CardSecretsPayload,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit,
    onDeleteCard: (DecryptedAccessCard) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val clipboard = LocalClipboardManager.current
    var revealSecrets by remember { mutableStateOf(false) }
    var showAdvancedEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val initialIconOption = remember(card.id, card.secrets.iconKey) {
        CardIconOption.fromRaw(card.secrets.iconKey)
    }

    var editLabel by remember(card.id, card.label) { mutableStateOf(card.label) }
    var editCategory by remember(card.id, card.category) { mutableStateOf(card.category) }
    var editAccentColor by remember(card.id, card.accentColor) { mutableStateOf(card.accentColor) }
    var editIconOption by remember(card.id, card.secrets.iconKey) { mutableStateOf(initialIconOption) }

    var editAid by remember(card.id, card.secrets.aidHex) { mutableStateOf(card.secrets.aidHex) }
    var editApdu by remember(card.id, card.secrets.apduResponseHex) { mutableStateOf(card.secrets.apduResponseHex) }
    var editNotes by remember(card.id, card.secrets.notes) { mutableStateOf(card.secrets.notes) }

    var isSavingCustomization by remember { mutableStateOf(false) }
    var customizeError by remember { mutableStateOf<String?>(null) }
    var advancedError by remember { mutableStateOf<String?>(null) }

    val hasAppearanceChanges = editLabel.trim() != card.label ||
        editCategory != card.category ||
        editAccentColor != card.accentColor ||
        editIconOption != initialIconOption

    // Live interactive card preview reflecting rename, personalized icon & accent color edits
    val previewCard = remember(card, editLabel, editCategory, editAccentColor, editIconOption) {
        val nextIconKey = if (editIconOption == CardIconOption.AUTO) "" else editIconOption.name
        card.copy(
            label = editLabel.trim().ifEmpty { card.label },
            category = editCategory,
            accentColor = editAccentColor,
            secrets = card.secrets.copy(iconKey = nextIconKey)
        )
    }

    val updatedAtText = remember(card.updatedAt, card.createdAt) {
        formatVaultTimestamp(card.updatedAt ?: card.createdAt)
    }
    val createdAtText = remember(card.createdAt) {
        formatVaultTimestamp(card.createdAt)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultObsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回卡包",
                            tint = VaultTextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "门禁卡详情预览",
                            style = MaterialTheme.typography.headlineSmall,
                            color = VaultTextPrimary
                        )
                        if (hasAppearanceChanges) {
                            Text(
                                text = "实时预览中 · 尚未保存修改",
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultCyan
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = { showAdvancedEdit = !showAdvancedEdit },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑技术参数与备注",
                            tint = if (showAdvancedEdit) VaultCyan else VaultTextSecondary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除卡片",
                            tint = VaultRose
                        )
                    }
                }
            }

            // Live Card Preview
            AccessCardVisual(
                card = previewCard,
                onClick = {},
                onToggleHce = { onToggleHce(card) }
            )

            // Encrypted Storage Timestamp & Security Status Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "加密存储最后更新时间",
                                style = MaterialTheme.typography.labelLarge,
                                color = VaultTextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = VaultCyanContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = VaultCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "AES-256-GCM",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultCyan
                                )
                            }
                        }
                    }

                    Text(
                        text = updatedAtText,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("detail_last_updated_timestamp")
                    )

                    Text(
                        text = "首次写入: $createdAtText · HCE 刷卡次数: ${card.emulationCount} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultTextSecondary
                    )
                }
            }

            // Rename & Personalized Icon Customization Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, if (hasAppearanceChanges) VaultCyan else VaultBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = VaultCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "卡片重命名与个性化图标",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { if (it.length <= 40) editLabel = it },
                        label = { Text("卡片名称（支持随时重命名）") },
                        singleLine = true,
                        colors = vaultTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_rename_input")
                    )

                    Text(
                        text = "选择个性化图标",
                        style = MaterialTheme.typography.labelLarge,
                        color = VaultTextSecondary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(CardIconOption.entries) { option ->
                            val selected = editIconOption == option
                            val tileColor = editAccentColor.toColor()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) tileColor.copy(alpha = 0.16f) else VaultSurfaceElevated,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) tileColor else VaultBorder
                                ),
                                modifier = Modifier
                                    .width(74.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { editIconOption = option }
                                    .testTag("icon_picker_${option.name}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = option.toIcon(editCategory),
                                        contentDescription = option.displayName,
                                        tint = if (selected) tileColor else VaultTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) tileColor else VaultTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "主题色与场所分类",
                        style = MaterialTheme.typography.labelLarge,
                        color = VaultTextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardAccentColor.entries.forEach { accent ->
                            val c = accent.toColor()
                            val selected = editAccentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(c.copy(alpha = 0.22f))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) c else VaultBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { editAccentColor = accent }
                                    .testTag("detail_color_${accent.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = accent.displayName,
                                        tint = c,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CardCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultCyanContainer,
                                    selectedLabelColor = VaultCyan,
                                    containerColor = VaultSurfaceElevated,
                                    labelColor = VaultTextSecondary
                                )
                            )
                        }
                    }

                    if (customizeError != null) {
                        Text(
                            text = customizeError.orEmpty(),
                            color = VaultRose,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = {
                            val cleanLabel = editLabel.trim()
                            if (cleanLabel.isEmpty()) {
                                customizeError = "卡片名称不能为空"
                                return@Button
                            }
                            customizeError = null
                            isSavingCustomization = true
                            val nextIconKey = if (editIconOption == CardIconOption.AUTO) "" else editIconOption.name
                            onUpdateCard(
                                card,
                                cleanLabel,
                                editCategory,
                                editAccentColor,
                                card.secrets.copy(iconKey = nextIconKey)
                            ) { res ->
                                isSavingCustomization = false
                                if (res.isSuccess) {
                                    onShowMessage("已重新加密保存卡片名称与个性化图标")
                                } else {
                                    customizeError = res.exceptionOrNull()?.localizedMessage ?: "保存失败，请重试"
                                }
                            }
                        },
                        enabled = hasAppearanceChanges && !isSavingCustomization,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultCyan,
                            contentColor = VaultObsidian,
                            disabledContainerColor = VaultSurfaceElevated,
                            disabledContentColor = VaultTextSecondary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("detail_save_customization_button")
                    ) {
                        if (isSavingCustomization) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = VaultObsidian
                            )
                        } else {
                            Text(
                                text = if (hasAppearanceChanges) "保存重命名与个性化图标" else "当前外观已同步至云端",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // HCE Primary CTA
            if (card.hceSupported) {
                Button(
                    onClick = { onToggleHce(card) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (card.activeHce) VaultSurfaceElevated else VaultCyan,
                        contentColor = if (card.activeHce) VaultCyan else VaultObsidian
                    ),
                    border = if (card.activeHce) BorderStroke(1.dp, VaultCyan) else null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("detail_toggle_hce_button")
                ) {
                    Icon(imageVector = Icons.Filled.Contactless, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (card.activeHce) "已激活 HCE 模拟刷卡（点击停用）" else "设为当前活跃 HCE 模拟卡",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "HCE 模拟为固定应答重放，适用于静态校验门禁；不支持挑战-响应式加密认证读卡器。",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultTextSecondary
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VaultAmberContainer,
                    border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = VaultAmber
                        )
                        Text(
                            text = "此卡片协议为 ${card.primaryTech.displayName}。受限于 Android 系统 NFC 控制器在 HCE 模式下自动随机生成 UID，不支持直接模拟固定 UID 扇区卡；您仍可随时安全查看并同步该卡的加密档案。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultTextPrimary
                        )
                    }
                }
            }

            // Decrypted Technical Credentials
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已解密的技术凭证",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                        TextButton(
                            onClick = { revealSecrets = !revealSecrets },
                            modifier = Modifier.testTag("toggle_reveal_secrets")
                        ) {
                            Icon(
                                imageVector = if (revealSecrets) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(if (revealSecrets) "掩码隐藏" else "显示明文", color = VaultCyan)
                        }
                    }

                    SecretFieldRow(
                        label = "物理 UID",
                        value = card.secrets.uidHex.ifEmpty { "未设置" },
                        revealed = revealSecrets,
                        onCopy = {
                            clipboard.setText(AnnotatedString(card.secrets.uidHex))
                            onShowMessage("UID 已复制到剪贴板")
                        }
                    )
                    if (card.secrets.aidHex.isNotEmpty()) {
                        SecretFieldRow(
                            label = "HCE AID",
                            value = card.secrets.aidHex,
                            revealed = revealSecrets,
                            onCopy = {
                                clipboard.setText(AnnotatedString(card.secrets.aidHex))
                                onShowMessage("AID 已复制到剪贴板")
                            }
                        )
                    }
                    if (card.secrets.apduResponseHex.isNotEmpty()) {
                        SecretFieldRow(
                            label = "APDU 响应载荷",
                            value = card.secrets.apduResponseHex,
                            revealed = revealSecrets,
                            onCopy = {
                                clipboard.setText(AnnotatedString(card.secrets.apduResponseHex))
                                onShowMessage("APDU 载荷已复制")
                            }
                        )
                    }
                    if (card.secrets.atqaHex.isNotEmpty() || card.secrets.sakHex.isNotEmpty()) {
                        SecretFieldRow(
                            label = "ATQA / SAK",
                            value = "ATQA=${card.secrets.atqaHex}  SAK=${card.secrets.sakHex}",
                            revealed = revealSecrets
                        )
                    }
                    if (card.secrets.techList.isNotEmpty()) {
                        SecretFieldRow(
                            label = "NFC 技术栈",
                            value = card.secrets.techList.joinToString(", "),
                            revealed = true
                        )
                    }
                    if (card.secrets.ndefSummary.isNotEmpty()) {
                        SecretFieldRow(
                            label = "NDEF 数据",
                            value = card.secrets.ndefSummary,
                            revealed = revealSecrets
                        )
                    }
                    if (card.secrets.notes.isNotEmpty()) {
                        SecretFieldRow(
                            label = "加密备注",
                            value = card.secrets.notes,
                            revealed = revealSecrets
                        )
                    }
                }
            }

            // Advanced AID / APDU / Notes Editor
            AnimatedVisibility(visible = showAdvancedEdit) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VaultSurfaceElevated,
                    border = BorderStroke(1.dp, VaultCyan)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "编辑底层协议参数与加密备注",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultCyan
                        )

                        if (card.hceSupported) {
                            OutlinedTextField(
                                value = editAid,
                                onValueChange = { editAid = it },
                                label = { Text("HCE AID (Hex)") },
                                singleLine = true,
                                colors = vaultTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editApdu,
                                onValueChange = { editApdu = it },
                                label = { Text("APDU 响应载荷 (Hex)") },
                                singleLine = true,
                                colors = vaultTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it.take(MAX_NOTES_LENGTH) },
                            label = { Text("加密备注") },
                            supportingText = {
                                Text("${editNotes.length} / $MAX_NOTES_LENGTH")
                            },
                            minLines = 2,
                            colors = vaultTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (advancedError != null) {
                            Text(advancedError.orEmpty(), color = VaultRose, style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAdvancedEdit = false },
                                border = BorderStroke(1.dp, VaultBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("收起", color = VaultTextSecondary)
                            }
                            Button(
                                onClick = {
                                    val cleanLabel = editLabel.trim().ifEmpty { card.label }
                                    val cleanAid = NfcTagParser.normalizeHex(editAid)
                                    if (card.hceSupported && cleanAid.isNotEmpty() && !NfcTagParser.isValidAidHex(cleanAid)) {
                                        advancedError = "AID 须为 10~32 位偶数 Hex"
                                        return@Button
                                    }
                                    val cleanApdu = NfcTagParser.normalizeHex(editApdu)
                                    if (cleanApdu.isNotEmpty() && cleanApdu.length % 2 != 0) {
                                        advancedError = "APDU Hex 长度必须为偶数"
                                        return@Button
                                    }
                                    advancedError = null
                                    val nextIconKey = if (editIconOption == CardIconOption.AUTO) "" else editIconOption.name
                                    onUpdateCard(
                                        card,
                                        cleanLabel,
                                        editCategory,
                                        editAccentColor,
                                        card.secrets.copy(
                                            aidHex = if (card.hceSupported) cleanAid.ifEmpty { NfcTagParser.DEFAULT_HCE_AID } else card.secrets.aidHex,
                                            apduResponseHex = cleanApdu,
                                            notes = editNotes.trim(),
                                            iconKey = nextIconKey
                                        )
                                    ) { res ->
                                        if (res.isSuccess) {
                                            showAdvancedEdit = false
                                            onShowMessage("技术参数已重新加密并同步")
                                        } else {
                                            advancedError = res.exceptionOrNull()?.localizedMessage ?: "更新失败"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VaultCyan,
                                    contentColor = VaultObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_save_edit_button")
                            ) {
                                Text("保存参数", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = VaultSurfaceElevated,
            title = { Text("确认删除门禁卡？", color = VaultTextPrimary) },
            text = {
                Text(
                    "将从本地缓存及云端保险库永久移除「${card.label}」的加密凭证，此操作不可撤销。",
                    color = VaultTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteCard(card)
                    }
                ) {
                    Text("确认删除", color = VaultRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = VaultTextSecondary)
                }
            }
        )
    }
}

private fun formatVaultTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "刚刚加密同步"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
}

@Composable
private fun SecretFieldRow(
    label: String,
    value: String,
    revealed: Boolean,
    onCopy: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VaultObsidian,
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultTextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (revealed) value else "••••••••••••••••",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultTextPrimary
                )
            }
            if (onCopy != null && revealed && value.isNotBlank()) {
                IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制$label",
                        tint = VaultCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private const val MAX_NOTES_LENGTH = 1500

package com.aistudio.nfcshield.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.auth.GoogleAuthHelper
import com.aistudio.nfcshield.ui.components.NfcLiveStatusBanner
import com.aistudio.nfcshield.ui.components.openSystemNfcSettings
import com.aistudio.nfcshield.ui.components.rememberLiveNfcStatus
import com.aistudio.nfcshield.ui.theme.VaultBorderSubtle
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultErrorContainer
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultOnPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val liveNfcStatus by rememberLiveNfcStatus()

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onComplete = { isCheckingSilent = false }
        )
    }

    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                NfcLiveStatusBanner(
                    nfcStatus = liveNfcStatus,
                    onOpenNfcSettings = { openSystemNfcSettings(context) }
                )

                // Minimalist Studio Hero Card
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, VaultHairlineStrong),
                    color = colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(228.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_wallet_hero_minimal_1795181172457),
                            contentDescription = "盾匙 NFC 加密卡包极简主视觉",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            VaultObsidian.copy(alpha = 0.55f),
                                            VaultObsidian.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_icon_shield_minimal_1795181173160),
                                contentDescription = "盾匙 NFC 品牌徽标",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .border(1.dp, VaultHairlineStrong, RoundedCornerShape(13.dp))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.auth_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = VaultTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.auth_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                    }
                }

                // Unified Quiet Security Pillars Surface (Linear hairline list)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = VaultSurface,
                    border = BorderStroke(1.dp, VaultHairline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SecurityFeatureRow(
                            icon = Icons.Filled.VerifiedUser,
                            iconTint = VaultCyan,
                            title = stringResource(R.string.auth_feature_crypto_title),
                            description = stringResource(R.string.auth_feature_crypto_desc)
                        )
                        HorizontalDivider(
                            color = VaultBorderSubtle,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SecurityFeatureRow(
                            icon = Icons.Filled.Fingerprint,
                            iconTint = VaultTextPrimary,
                            title = stringResource(R.string.auth_feature_biometric_title),
                            description = stringResource(R.string.auth_feature_biometric_desc)
                        )
                        HorizontalDivider(
                            color = VaultBorderSubtle,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SecurityFeatureRow(
                            icon = Icons.Filled.Nfc,
                            iconTint = VaultTextPrimary,
                            title = stringResource(R.string.auth_feature_nfc_title),
                            description = stringResource(R.string.auth_feature_nfc_desc)
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VaultErrorContainer,
                        border = BorderStroke(1.dp, VaultRose.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultRose,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Monochrome high-contrast Apple/Linear primary CTA
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            errorMessage = "当前窗口上下文不支持登录"
                            return@Button
                        }
                        errorMessage = null
                        isLoading = true
                        GoogleAuthHelper.signInWithGoogle(
                            activity = activity,
                            credentialManager = credentialManager,
                            scope = scope,
                            onSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onCancelled = {
                                isLoading = false
                            },
                            onError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        disabledContainerColor = colorScheme.surfaceContainer,
                        disabledContentColor = colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isLoading || isCheckingSilent) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.auth_signing_in),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_google_sign_in),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VaultSurfaceElevated)
                .border(1.dp, VaultHairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VaultTextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VaultTextSecondary
            )
        }
    }
}

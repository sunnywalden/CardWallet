package com.aistudio.nfcshield

import android.app.Application
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.EncryptedCardDoc
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.VaultConfigDoc
import com.aistudio.nfcshield.data.model.VaultSavedStateCodec
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultSavedStateAndLifecycleTest {

    @Test
    fun `VaultSavedStateCodec round-trips encrypted cards and vault config cleanly`() {
        val now = Timestamp(1_760_000_000L, 123_000)
        val cards = listOf(
            EncryptedCardDoc(
                id = "card-transit-1",
                userId = "uid-abc",
                label = "长安通",
                category = CardCategory.TRANSIT.name,
                accentColor = CardAccentColor.ROSE.name,
                primaryTech = NfcTechType.ISO_DEP.name,
                maskedUid = "D1:**:**:BD",
                hceSupported = true,
                activeHce = true,
                encryptedPayloadBase64 = "QWxhZGRpbjpvcGVuIHNlc2FtZTEyMzQ1Njc4OTA=",
                payloadIvBase64 = "MTIzNDU2Nzg5MDEy",
                cryptoVersion = 2L,
                emulationCount = 5L,
                lastEmulatedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        val config = VaultConfigDoc(
            id = "config",
            userId = "uid-abc",
            wrappedDekBase64 = "d3JhcHBlZC1kZWstYmFzZTY0LXBheWxvYWQ=",
            wrappedDekIvBase64 = "MTIzNDU2Nzg5MDEy",
            kdfSaltBase64 = "c2FsdC1iYXNlNjQ=",
            kdfIterations = 120000L,
            pinVerifierBase64 = "dmVyaWZpZXItYmFzZTY0LXBheWxvYWQ=",
            kdfAlgorithm = "PBKDF2WithHmacSHA256",
            schemaVersion = 2L,
            createdAt = now,
            updatedAt = now
        )

        val handle = SavedStateHandle()
        handle["cards"] = VaultSavedStateCodec.encodeEncryptedCards(cards)
        handle["config"] = VaultSavedStateCodec.encodeVaultConfig(config)

        val restoredCards = VaultSavedStateCodec.decodeEncryptedCards(handle.get<String>("cards"))
        val restoredConfig = VaultSavedStateCodec.decodeVaultConfig(handle.get<String>("config"))

        assertNotNull(restoredCards)
        assertEquals(1, restoredCards!!.size)
        assertEquals("card-transit-1", restoredCards[0].id)
        assertEquals("长安通", restoredCards[0].label)
        assertEquals(true, restoredCards[0].activeHce)
        assertEquals(2L, restoredCards[0].cryptoVersion)
        assertEquals(now.seconds, restoredCards[0].createdAt?.seconds)

        assertNotNull(restoredConfig)
        assertEquals("uid-abc", restoredConfig!!.userId)
        assertEquals(120000L, restoredConfig.kdfIterations)
        assertEquals(2L, restoredConfig.schemaVersion)
    }

    @Test
    fun `VaultSavedStateCodec preserves schema v3 deviceBinding across restore`() {
        val config = VaultConfigDoc(
            id = "config",
            userId = "uid-v3",
            wrappedDekBase64 = "ZGV2aWNlLXNlYWxlZC1wYXlsb2Fk",
            wrappedDekIvBase64 = "MTIzNDU2Nzg5MDEy",
            kdfSaltBase64 = "c2FsdC1iYXNlNjQ=",
            kdfIterations = 120000L,
            pinVerifierBase64 = "device-bound:v3",
            kdfAlgorithm = "PBKDF2WithHmacSHA256",
            schemaVersion = 3L,
            deviceBinding = "ANDROID_KEYSTORE"
        )

        val restored = VaultSavedStateCodec.decodeVaultConfig(
            VaultSavedStateCodec.encodeVaultConfig(config)
        )

        assertNotNull(restored)
        assertEquals(3L, restored!!.schemaVersion)
        assertEquals("ANDROID_KEYSTORE", restored.deviceBinding)
    }

    @Test
    fun `VaultSavedStateCodec bounds payload size and safely rejects corrupt json`() {
        val hugeCards = (1..120).map { i ->
            EncryptedCardDoc(
                id = "card-$i",
                userId = "uid-big",
                label = "门禁卡 $i",
                encryptedPayloadBase64 = "A".repeat(2048),
                payloadIvBase64 = "MTIzNDU2Nzg5MDEy",
                cryptoVersion = 2L
            )
        }
        val encoded = VaultSavedStateCodec.encodeEncryptedCards(hugeCards)
        assertNotNull(encoded)
        assertTrue(
            "SavedStateHandle payload must stay bounded under 128 KB",
            encoded!!.toByteArray(Charsets.UTF_8).size <= 128 * 1024
        )
        assertNull(VaultSavedStateCodec.decodeEncryptedCards("{not-valid-json"))
        assertNull(VaultSavedStateCodec.decodeVaultConfig("[]"))
    }

    @Test
    fun `MainActivity survives lock-pause-stop and start-resume NFC rebuild cycle without crash`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        // Simulate screen lock / background suspension and subsequent wake-up rebuild
        controller
            .pause()
            .stop()
            .start()
            .resume()
            .pause()
            .stop()
            .destroy()
    }

    @Test
    fun `offline encrypted cache is per-account, keeps ciphertext and never wipes on empty snapshots`() =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val store = LocalVaultStore(app)
            store.clearSensitiveLocalSession()

            val config = VaultConfigDoc(
                id = "config",
                userId = "uid-cache-a",
                wrappedDekBase64 = "ZGV2aWNlLXNlYWxlZC1wYXlsb2Fk",
                wrappedDekIvBase64 = "MTIzNDU2Nzg5MDEy",
                kdfSaltBase64 = "c2FsdC1iYXNlNjQ=",
                kdfIterations = 120000L,
                pinVerifierBase64 = "device-bound:v3",
                schemaVersion = 3L,
                deviceBinding = "ANDROID_KEYSTORE"
            )
            val card = EncryptedCardDoc(
                id = "card-1",
                userId = "uid-cache-a",
                label = "南门门禁",
                encryptedPayloadBase64 = "Q0lQSEVSVEVYVA==",
                payloadIvBase64 = "MTIzNDU2Nzg5MDEy",
                cryptoVersion = 2L
            )

            store.saveCachedVaultConfig("uid-cache-a", config)
            store.saveCachedCards("uid-cache-a", listOf(card))

            val snapshot = store.readEncryptedVaultCache("uid-cache-a")
            assertNotNull(snapshot)
            assertEquals("ANDROID_KEYSTORE", snapshot!!.config?.deviceBinding)
            assertEquals(1, snapshot.cards.size)
            // Ciphertext only — the cache never stores decrypted notes/UID.
            assertEquals("Q0lQSEVSVEVYVA==", snapshot.cards.first().encryptedPayloadBase64)

            // Empty Firestore snapshots must not wipe a populated offline cache.
            store.saveCachedCards("uid-cache-a", emptyList())
            assertEquals(1, store.readEncryptedVaultCache("uid-cache-a")!!.cards.size)

            // Another account can never read this cache, and its first write resets the slot.
            assertNull(store.readEncryptedVaultCache("uid-cache-b"))
            store.saveCachedVaultConfig("uid-cache-b", config.copy(userId = "uid-cache-b"))
            assertNull(store.readEncryptedVaultCache("uid-cache-a"))

            store.clearSensitiveLocalSession()
            assertNull(store.readEncryptedVaultCache("uid-cache-b"))
        }

    @Test
    fun `MainActivity accepts an App Check debug token intent extra without crashing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val intent = Intent(app, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_APPCHECK_DEBUG_TOKEN, "11111111-2222-3333-4444-555555555555")
        Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .pause()
            .stop()
            .destroy()
    }

    @Test
    fun `CardSecretsPayload serializes normalized tags and defaults legacy json to empty`() {
        val payload = CardSecretsPayload(
            uidHex = "D1:EA:D7:BD",
            aidHex = "F04E464353484945",
            notes = "长安通",
            tags = listOf(" #通勤 ", "高频", "通勤")
        )
        val restored = CardSecretsPayload.fromJsonString(payload.toJsonString())
        assertEquals(listOf("通勤", "高频"), restored.tags)

        val legacyJson = """{"uidHex":"04:11:22:33","notes":"旧版卡片"}"""
        val legacyRestored = CardSecretsPayload.fromJsonString(legacyJson)
        assertTrue(legacyRestored.tags.isEmpty())
    }
}

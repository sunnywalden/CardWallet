package com.aistudio.nfcshield

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.ui.VaultAppRoot
import com.aistudio.nfcshield.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isResumed = false

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        dispatchCapturedTag(tag, "ReaderCallback")
    }

    private fun dispatchCapturedTag(tag: Tag, source: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val parsed = NfcTagParser.parseTag(tag, captureSource = source)
            HceRuntimeBus.recordParsedTag(parsed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                HceRuntimeBus.readerModeActive.collect { active ->
                    syncReaderMode(active)
                }
            }
        }

        setContent {
            MyApplicationTheme {
                VaultAppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        syncReaderMode(HceRuntimeBus.readerModeActive.value)
    }

    override fun onPause() {
        isResumed = false
        syncReaderMode(false)
        super.onPause()
    }

    private fun syncReaderMode(active: Boolean) {
        val adapter = nfcAdapter ?: return
        if (active && isResumed && adapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            runCatching {
                adapter.enableReaderMode(this, readerCallback, flags, null)
            }
        } else {
            runCatching {
                adapter.disableReaderMode(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag != null) {
            dispatchCapturedTag(tag, "NewIntent")
        }
    }
}

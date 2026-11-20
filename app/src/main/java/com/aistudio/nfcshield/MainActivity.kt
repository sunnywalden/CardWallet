package com.aistudio.nfcshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aistudio.nfcshield.nfc.HceAidManager
import com.aistudio.nfcshield.nfc.HceQuickTileService
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.ui.VaultAppRoot
import com.aistudio.nfcshield.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : FragmentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isResumed = false
    private var nfcStateReceiverRegistered = false

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
            if (state == NfcAdapter.STATE_ON && isResumed) {
                rebuildNfcListeners("ACTION_ADAPTER_STATE_CHANGED:STATE_ON")
            } else if (state == NfcAdapter.STATE_OFF || state == NfcAdapter.STATE_TURNING_OFF) {
                syncReaderMode(false)
            }
        }
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logStartupException("MainActivity.lifecycleScope", throwable)
    }

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        try {
            dispatchCapturedTag(tag, "ReaderCallback")
        } catch (t: Throwable) {
            logStartupException("MainActivity.readerCallback", t)
        }
    }

    private fun dispatchCapturedTag(tag: Tag, source: String) {
        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val parsed = NfcTagParser.parseTag(tag, captureSource = source)
                HceRuntimeBus.recordParsedTag(parsed)
            } catch (t: Throwable) {
                logStartupException("MainActivity.dispatchCapturedTag($source)", t)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installUncaughtExceptionHandler(this)
        recordInitStep("MainActivity.onCreate start (savedInstanceState=${savedInstanceState != null})")

        try {
            super.onCreate(savedInstanceState)
            recordInitStep("MainActivity.super.onCreate done")
        } catch (t: Throwable) {
            logStartupException("MainActivity.super.onCreate", t)
            throw t
        }

        try {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
            recordInitStep("enableEdgeToEdge done")
        } catch (t: Throwable) {
            logStartupException("MainActivity.enableEdgeToEdge", t)
        }

        try {
            val firebaseReady = FirebaseBootstrap.ensureInitialized(this)
            recordInitStep("FirebaseBootstrap.ensureInitialized=$firebaseReady")
        } catch (t: Throwable) {
            logStartupException("MainActivity.FirebaseBootstrap", t)
        }

        try {
            val intentDebugToken = appCheckDebugTokenFromIntent(intent)
            AppCheckInstaller.install(this, intentDebugToken)
            recordInitStep(
                "MainActivity AppCheck installed (intentDebugToken=${intentDebugToken != null})"
            )
        } catch (t: Throwable) {
            logStartupException("MainActivity.AppCheckInstaller", t)
        }

        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            recordInitStep(
                "NfcAdapter acquired (available=${nfcAdapter != null}, enabled=${runCatching { nfcAdapter?.isEnabled }.getOrNull()})"
            )
        } catch (t: Throwable) {
            logStartupException("MainActivity.NfcAdapter.getDefaultAdapter", t)
            nfcAdapter = null
        }

        try {
            lifecycleScope.launch(coroutineExceptionHandler) {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    HceRuntimeBus.readerModeActive.collect { active ->
                        syncReaderMode(active)
                    }
                }
            }
            recordInitStep("HceRuntimeBus readerMode collector launched")
        } catch (t: Throwable) {
            logStartupException("MainActivity.launchReaderModeCollector", t)
        }

        try {
            setContent {
                MyApplicationTheme {
                    VaultAppRoot()
                }
            }
            recordInitStep("Compose setContent(VaultAppRoot) attached")
        } catch (t: Throwable) {
            logStartupException("MainActivity.setContent", t)
            showEmergencyStartupFallback(t)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            refreshNfcAdapter()
            registerNfcStateReceiver()
            HceQuickTileService.requestTileSync(this)
            recordInitStep("MainActivity.onStart (nfcAvailable=${nfcAdapter != null})")
        } catch (t: Throwable) {
            logStartupException("MainActivity.onStart", t)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            isResumed = true
            rebuildNfcListeners("MainActivity.onResume")
        } catch (t: Throwable) {
            logStartupException("MainActivity.onResume", t)
        }
    }

    override fun onPause() {
        try {
            isResumed = false
            syncReaderMode(false)
        } catch (t: Throwable) {
            logStartupException("MainActivity.onPause", t)
        } finally {
            super.onPause()
        }
    }

    override fun onStop() {
        try {
            unregisterNfcStateReceiver()
        } catch (t: Throwable) {
            logStartupException("MainActivity.onStop", t)
        } finally {
            super.onStop()
        }
    }

    private fun refreshNfcAdapter() {
        nfcAdapter = runCatching { NfcAdapter.getDefaultAdapter(this) }
            .onFailure { logStartupException("MainActivity.refreshNfcAdapter", it) }
            .getOrNull()
    }

    private fun registerNfcStateReceiver() {
        if (nfcStateReceiverRegistered) return
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        nfcStateReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                this,
                nfcStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure { err ->
            Log.w(STARTUP_LOG_TAG, "Failed to register NFC state receiver: ${err.message}")
        }.isSuccess
    }

    private fun unregisterNfcStateReceiver() {
        if (!nfcStateReceiverRegistered) return
        nfcStateReceiverRegistered = false
        runCatching { unregisterReceiver(nfcStateReceiver) }
            .onFailure { err ->
                Log.w(STARTUP_LOG_TAG, "Failed to unregister NFC state receiver: ${err.message}")
            }
    }

    /**
     * Re-acquires the platform NFC adapter, re-binds active HCE AID routing if a card is active,
     * and restores foreground ReaderMode so NFC scanning/emulation never stalls after lock screen
     * or background suspension.
     */
    private fun rebuildNfcListeners(reason: String) {
        refreshNfcAdapter()
        recordInitStep("$reason (readerActive=${HceRuntimeBus.readerModeActive.value}, nfcEnabled=${runCatching { nfcAdapter?.isEnabled }.getOrNull()})")
        syncReaderMode(HceRuntimeBus.readerModeActive.value)
        val activeAid = HceRuntimeBus.getCachedActiveCard()?.aidHex
        if (!activeAid.isNullOrBlank()) {
            runCatching { HceAidManager.syncDynamicAid(this, activeAid) }
                .onFailure { Log.w(STARTUP_LOG_TAG, "HCE dynamic AID re-sync skipped: ${it.message}") }
        }
    }

    private fun syncReaderMode(active: Boolean) {
        if (isFinishing || isDestroyed) return
        val adapter = nfcAdapter ?: run {
            refreshNfcAdapter()
            nfcAdapter
        } ?: return
        try {
            if (active && isResumed && adapter.isEnabled) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
                adapter.enableReaderMode(this, readerCallback, flags, null)
            } else {
                adapter.disableReaderMode(this)
            }
        } catch (t: Throwable) {
            logStartupException("MainActivity.syncReaderMode(active=$active)", t)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            val debugToken = appCheckDebugTokenFromIntent(intent)
            if (debugToken != null) {
                // Persisted synchronously for the next App Check token fetch (debug builds only).
                AppCheckInstaller.install(this, debugToken)
                recordInitStep("MainActivity.onNewIntent debug AppCheck token override applied")
            }
        } catch (t: Throwable) {
            logStartupException("MainActivity.onNewIntent.AppCheckInstaller", t)
        }
        try {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                dispatchCapturedTag(tag, "NewIntent")
            }
        } catch (t: Throwable) {
            logStartupException("MainActivity.onNewIntent", t)
        }
    }

    private fun appCheckDebugTokenFromIntent(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_APPCHECK_DEBUG_TOKEN)
            ?: intent?.getStringExtra(EXTRA_FIREBASE_APPCHECK_DEBUG_TOKEN)

    private fun showEmergencyStartupFallback(throwable: Throwable) {
        runCatching {
            val scroll = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#07080A"))
            }
            val text = TextView(this).apply {
                setTextColor(Color.parseColor("#F5F7FA"))
                textSize = 13f
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad * 2, pad, pad)
                text = buildString {
                    appendLine("盾匙 NFC 启动初始化异常")
                    appendLine("请通过 adb logcat -s $STARTUP_LOG_TAG 查看完整堆栈：")
                    appendLine()
                    appendLine("${throwable.javaClass.name}: ${throwable.message}")
                    appendLine()
                    appendLine("初始化轨迹：")
                    appendLine(formatInitTrace())
                }
            }
            scroll.addView(text)
            setContentView(scroll)
        }
    }

    companion object {
        const val STARTUP_LOG_TAG = "ShieldVaultStartup"
        const val EXTRA_APPCHECK_DEBUG_TOKEN = "appcheck_debug_token"
        const val EXTRA_FIREBASE_APPCHECK_DEBUG_TOKEN = "firebase_app_check_debug_token"
        private const val MAX_TRACE_ENTRIES = 60
        private const val CRASH_REPORT_FILENAME = "last_startup_crash.log"

        private val handlerInstalled = AtomicBoolean(false)
        private val initSteps: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile
        private var crashFileDir: File? = null

        fun recordInitStep(step: String) {
            val ts = runCatching {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            }.getOrDefault("--:--:--")
            val entry = "[$ts] [thread=${Thread.currentThread().name}] $step"
            synchronized(initSteps) {
                if (initSteps.size >= MAX_TRACE_ENTRIES) {
                    initSteps.removeAt(0)
                }
                initSteps.add(entry)
            }
            Log.i(STARTUP_LOG_TAG, entry)
        }

        fun installUncaughtExceptionHandler(context: Context) {
            crashFileDir = runCatching { context.applicationContext?.filesDir ?: context.filesDir }.getOrNull()
            if (!handlerInstalled.compareAndSet(false, true)) return

            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val report = buildDiagnosticReport(
                        stage = "UncaughtException(thread=${thread.name}, id=${thread.id})",
                        throwable = throwable
                    )
                    logChunkedError(report)
                    persistCrashReport(report)
                } catch (loggingError: Throwable) {
                    Log.e(STARTUP_LOG_TAG, "Failed while formatting uncaught exception report", loggingError)
                } finally {
                    previousHandler?.uncaughtException(thread, throwable)
                }
            }
            recordInitStep("Global UncaughtExceptionHandler installed")
        }

        fun logStartupException(stage: String, throwable: Throwable) {
            val report = buildDiagnosticReport(stage = stage, throwable = throwable)
            logChunkedError(report)
            persistCrashReport(report)
        }

        private fun formatInitTrace(): String = synchronized(initSteps) {
            if (initSteps.isEmpty()) "(no init steps recorded)" else initSteps.joinToString("\n")
        }

        private fun buildDiagnosticReport(stage: String, throwable: Throwable): String {
            val stackTrace = Log.getStackTraceString(throwable)
            return buildString {
                appendLine("========== SHIELD VAULT DIAGNOSTIC REPORT ==========")
                appendLine("Stage: $stage")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, ABI=${Build.SUPPORTED_ABIS.joinToString()})")
                appendLine("Thread: ${Thread.currentThread().name}")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("--- Initialization Trace ---")
                appendLine(formatInitTrace())
                appendLine("--- Full Stack Trace ---")
                appendLine(stackTrace)
                appendLine("====================================================")
            }
        }

        private fun logChunkedError(message: String) {
            val chunkSize = 3500
            if (message.length <= chunkSize) {
                Log.e(STARTUP_LOG_TAG, message)
                return
            }
            var offset = 0
            while (offset < message.length) {
                val end = (offset + chunkSize).coerceAtMost(message.length)
                Log.e(STARTUP_LOG_TAG, message.substring(offset, end))
                offset = end
            }
        }

        private fun persistCrashReport(report: String) {
            val dir = crashFileDir ?: return
            runCatching {
                File(dir, CRASH_REPORT_FILENAME).writeText(report)
            }
        }
    }
}

package com.aistudio.nfcshield

import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    override fun onResume() {
        super.onResume()
        try {
            isResumed = true
            recordInitStep("MainActivity.onResume")
            syncReaderMode(HceRuntimeBus.readerModeActive.value)
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

    private fun syncReaderMode(active: Boolean) {
        val adapter = nfcAdapter ?: return
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

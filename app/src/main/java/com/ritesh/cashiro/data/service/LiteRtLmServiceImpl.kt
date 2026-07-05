package com.ritesh.cashiro.data.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.ritesh.cashiro.domain.service.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LiteRtLmServiceImpl"

/**
 * LiteRT-LM (com.google.ai.edge.litertlm) implementation of [LlmService].
 *
 * LiteRT-LM (com.google.ai.edge.litertlm) framework supporting .litertlm models (e.g. Qwen2.5-1.5B-Instruct).
 *
 * Hardware backend: GPU (OpenCL) is attempted first, falling back to CPU when
 * libOpenCL.so is unavailable on the device.
 *
 * Sampler defaults (tuned for financial transaction parsing):
 *   - topK = 10       → focused, near-deterministic token selection
 *   - topP = 0.95     → nucleus sampling with slight variation
 *   - temperature = 0.8 → reduces hallucinations for structured data extraction
 *
 * Lifecycle is managed by [com.ritesh.cashiro.di.LlmModule] which constructs this
 * class as a Singleton when BuildConfig.USE_LITERT_LM = true.
 */
class LiteRtLmServiceImpl(
    private val context: Context
) : LlmService {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /** Returns true once [initialize] has successfully loaded the model. */
    override fun isInitialized(): Boolean = engine != null

    /**
     * Loads the model from [modelPath] into the LiteRT-LM runtime.
     * Must be called on a background thread (dispatches to [Dispatchers.IO]).
     *
     * GPU (OpenCL) is attempted first; falls back silently to CPU on failure.
     */
    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (engine != null) {
            Log.d(TAG, "Already initialized — skipping.")
            return@withContext Result.success(Unit)
        }

        try {
            val cacheDir = File(context.cacheDir, "litert_lm_cache").also { it.mkdirs() }
            val backend = resolveBackend()

            // EngineConfig is a Kotlin data class — use named parameters directly.
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = cacheDir.absolutePath
            )

            Log.d(TAG, "Initializing LiteRT-LM engine. backend=$backend, model=$modelPath")
            engine = Engine(config).also { it.initialize() }
            Log.d(TAG, "LiteRT-LM engine initialized successfully.")

            // Create a conversation session with tuned sampler parameters.
            conversation = engine!!.createConversation(buildConversationConfig())
            Log.d(TAG, "Conversation session created.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT-LM engine", e)
            engine = null
            conversation = null
            Result.failure(e)
        }
    }

    /**
     * Blocking single-shot response generation.
     * Collects the streaming flow and concatenates all text chunks into one string.
     */
    override suspend fun generateResponse(prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                generateResponseStream(prompt).collect { chunk -> sb.append(chunk) }
                Result.success(sb.toString())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "generateResponse error", e)
                com.ritesh.cashiro.utils.CrashHandler.triggerCrash(context, e)
                Result.failure(e)
            }
        }

    /**
     * Streaming response via Kotlin [Flow].
     * Each emission is a partial text chunk extracted from the model's [Message].
     *
     * [Conversation.sendMessageAsync] returns a [Flow] of [Message] objects.
     * Each [Message] carries a [Contents] bag of [Content] parts; we collect only
     * [Content.Text] parts and join them into a single string per emission.
     */
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val conv = conversation
            ?: throw IllegalStateException("LiteRT-LM not initialized. Call initialize() first.")

        Log.d(TAG, "Sending message (first 80 chars): ${prompt.take(80)}…")

        conv.sendMessageAsync(prompt)
            .map { message ->
                // Extract all text parts from the Message's Contents bag
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }
            .collect { textChunk ->
                if (textChunk.isNotEmpty()) emit(textChunk)
            }
    }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e(TAG, "Fatal error in LiteRT-LM stream", e)
        // Route unhandled native/JNI flow exceptions to our crash screen
        com.ritesh.cashiro.utils.CrashHandler.triggerCrash(context, e)
        throw e // Re-throw to cancel the flow
    }.flowOn(Dispatchers.IO)

    /**
     * Resets the active conversation session WITHOUT unloading the engine.
     * Call this at the start of each new chat to clear the model's internal KV-cache history.
     */
    override suspend fun resetConversation(): Unit = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resetting conversation (keeping engine loaded).")
        try { conversation?.close() } catch (_: Exception) { }
        conversation = engine?.createConversation(buildConversationConfig())
        Log.d(TAG, "Conversation reset. New session ready.")
    }

    /**
     * Closes the active conversation and shuts down the engine.
     * After reset, [initialize] must be called again before using the service.
     */
    override suspend fun reset() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resetting LiteRT-LM engine.")
        try { conversation?.close() } catch (_: Exception) { }
        conversation = null
        try { engine?.close() } catch (_: Exception) { }
        engine = null
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Attempts to load OpenCL native library to confirm GPU availability.
     * Falls back to CPU backend silently when OpenCL is absent.
     */
    private fun resolveBackend(): Backend {
        return try {
            System.loadLibrary("OpenCL")
            Log.d(TAG, "OpenCL available — using GPU backend.")
            Backend.GPU()
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "OpenCL not available — using CPU backend.")
            Backend.CPU()
        }
    }

    /**
     * Builds a [ConversationConfig] with a [SamplerConfig] suited for financial parsing.
     *
     * Note: [SamplerConfig] takes [Double] parameters (not Float).
     */
    private fun buildConversationConfig(): ConversationConfig {
        val sampler = SamplerConfig(
            topK = 10,
            topP = 0.95,
            temperature = 0.3  // Lower temperature = more focused, less hallucinatory output
        )
        return ConversationConfig(samplerConfig = sampler)
    }
}

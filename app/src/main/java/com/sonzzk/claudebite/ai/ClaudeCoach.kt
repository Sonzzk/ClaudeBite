package com.sonzzk.claudebite.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.fasterxml.jackson.core.JacksonException
import com.sonzzk.claudebite.ai.dto.FoodAnalysisParser
import com.sonzzk.claudebite.ai.dto.FoodAnalysisSchema
import com.sonzzk.claudebite.ai.dto.toFoodAnalysis
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.prefs.SecureSettings
import java.io.IOException
import java.time.Duration
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The real [AiCoach]: talks to Claude through the official Anthropic Java SDK.
 *
 * - Picks a model per job with [ModelRouter] (cheap Haiku where it is good enough).
 * - Food analysis uses structured outputs, so Claude answers with pure JSON (no chatty prose to pay for).
 * - Chat streams the reply so the UI can show a typing effect.
 * - Every call records its token usage so Settings can show the estimated cost.
 */
class ClaudeCoach(
    private val settings: SecureSettings,
    private val repository: HealthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiCoach {

    // ---------- Client (rebuilt when the API key changes) ----------

    private val clientLock = Any()
    private var cachedKey: String? = null
    private var cachedClient: AnthropicClient? = null

    /** Returns a client for the current key, or throws [AiError.NoApiKey]. */
    private fun client(): AnthropicClient {
        val key = settings.apiKey.value.trim()
        if (key.isEmpty()) throw AiError.NoApiKey()
        synchronized(clientLock) {
            val existing = cachedClient
            if (existing != null && key == cachedKey) return existing
            // We don't close the old client: a request might still be using it. Its idle
            // connections and threads shut themselves down after a minute.
            val fresh = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .maxRetries(2)
                .timeout(Duration.ofSeconds(90))
                .build()
            cachedKey = key
            cachedClient = fresh
            return fresh
        }
    }

    // ---------- Food ----------

    override suspend fun analyzeFoodPhoto(jpegBytes: ByteArray, userHint: String?, context: CoachContext): FoodAnalysis {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .data(Base64.getEncoder().encodeToString(jpegBytes))
                    .build(),
            )
            .build()
        val text = TextBlockParam.builder().text(Prompts.foodPhotoUserMessage(userHint, context)).build()
        return analyzeFood(AiTask.FOOD_PHOTO, listOf(ContentBlockParam.ofImage(image), ContentBlockParam.ofText(text)))
    }

    override suspend fun analyzeFoodText(description: String, context: CoachContext): FoodAnalysis {
        val text = TextBlockParam.builder().text(Prompts.foodTextUserMessage(description, context)).build()
        return analyzeFood(AiTask.FOOD_TEXT, listOf(ContentBlockParam.ofText(text)))
    }

    private suspend fun analyzeFood(task: AiTask, userContent: List<ContentBlockParam>): FoodAnalysis = aiCall {
        val choice = ModelRouter.route(task, settings.modelMode.value)
        val params = baseParams(choice, Prompts.FOOD_SYSTEM)
            .outputConfig(
                OutputConfig.builder()
                    .effort(choice.effort.toSdk())
                    .format(JsonOutputFormat.builder().schema(foodSchema).build())
                    .build(),
            )
            .addUserMessageOfBlockParams(userContent)
            .build()

        val message = withContext(ioDispatcher) { client().messages().create(params) }
        val model = message.model().asString()
        recordUsage(model, message)

        if (message.stopReason().orElse(null) == StopReason.REFUSAL) throw AiError.Refused()
        if (message.stopReason().orElse(null) == StopReason.MAX_TOKENS) {
            throw AiError.Other("That was a lot of food to describe! Try fewer items at once~")
        }

        val json = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        val dto = try {
            FoodAnalysisParser.parse(json)
        } catch (e: JacksonException) {
            throw AiError.Other("I got a bit confused reading that meal. Please try again!")
        }
        val analysis = dto.toFoodAnalysis(model)
        if (analysis.items.isEmpty()) {
            throw AiError.Other(analysis.comment.ifBlank { "I couldn't spot any food there~ Try another photo?" })
        }
        analysis
    }

    // ---------- Chat ----------

    override suspend fun chat(
        history: List<ChatMessage>,
        userMessage: String,
        context: CoachContext,
        onPartial: (String) -> Unit,
    ): ChatReply = aiCall {
        val choice = ModelRouter.route(ModelRouter.classifyChat(userMessage), settings.modelMode.value)
        val builder = baseParams(choice, Prompts.CHAT_SYSTEM)
            .outputConfig(OutputConfig.builder().effort(choice.effort.toSdk()).build())

        val turns = Prompts.buildTurns(history, userMessage)
        turns.forEachIndexed { index, turn ->
            when {
                // Only the newest user turn carries the context line (keeps earlier turns cacheable and cheap).
                index == turns.lastIndex -> builder.addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofText(TextBlockParam.builder().text(Prompts.contextLine(context)).build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(turn.text).build()),
                    ),
                )
                turn.isUser -> builder.addUserMessage(turn.text)
                else -> builder.addAssistantMessage(turn.text)
            }
        }
        val params = builder.build()

        withContext(ioDispatcher) {
            val text = StringBuilder()
            var model = choice.modelId
            var inputTokens = 0L
            var outputTokens = 0L
            var stopReason: StopReason? = null

            client().messages().createStreaming(params).use { stream ->
                val events = stream.stream().iterator()
                while (events.hasNext()) {
                    ensureActive() // stop reading (and close the stream) if the user leaves the screen
                    val event = events.next()
                    event.messageStart().ifPresent { start ->
                        val msg = start.message()
                        model = msg.model().asString() // may differ from choice if a fallback model answered
                        val usage = msg.usage()
                        inputTokens = usage.inputTokens() +
                            usage.cacheCreationInputTokens().orElse(0L) +
                            usage.cacheReadInputTokens().orElse(0L)
                        outputTokens = usage.outputTokens()
                    }
                    event.contentBlockDelta().ifPresent { delta ->
                        delta.delta().text().ifPresent { textDelta ->
                            text.append(textDelta.text())
                            onPartial(text.toString())
                        }
                    }
                    event.messageDelta().ifPresent { delta ->
                        delta.delta().stopReason().ifPresent { stopReason = it }
                        // message_delta usage is cumulative, so it replaces the start values.
                        val usage = delta.usage()
                        outputTokens = usage.outputTokens()
                        val totalInput = usage.inputTokens().orElse(0L) +
                            usage.cacheCreationInputTokens().orElse(0L) +
                            usage.cacheReadInputTokens().orElse(0L)
                        if (totalInput > 0) inputTokens = totalInput
                    }
                }
            }

            recordUsage(model, inputTokens, outputTokens)
            if (stopReason == StopReason.REFUSAL) throw AiError.Refused()
            val reply = text.toString().trim()
            if (reply.isEmpty()) throw AiError.Other("Hmm, I lost my words for a second. Could you ask again?")
            ChatReply(text = if (stopReason == StopReason.MAX_TOKENS) "$reply…" else reply, model = model)
        }
    }

    // ---------- Shared helpers ----------

    /**
     * Common request setup: model, output cap, the cached system prompt, and (for Sonnet/Opus)
     * the server-side refusal fallback opt-in.
     */
    private fun baseParams(choice: ModelChoice, system: String): MessageCreateParams.Builder {
        val builder = MessageCreateParams.builder()
            .model(choice.modelId)
            .maxTokens(choice.maxTokens)
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(system)
                        // Harmless if the prompt is below the minimum cacheable size; saves money when it isn't.
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
        if (ModelRouter.supportsServerFallback(choice.modelId)) {
            // If a safety classifier declines, the API retries on Anthropic's recommended model.
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder
    }

    private suspend fun recordUsage(model: String, message: Message) {
        val usage = message.usage()
        val input = usage.inputTokens() +
            usage.cacheCreationInputTokens().orElse(0L) +
            usage.cacheReadInputTokens().orElse(0L)
        recordUsage(model, input, usage.outputTokens())
    }

    private suspend fun recordUsage(model: String, input: Long, output: Long) {
        if (input == 0L && output == 0L) return
        // Never let a database hiccup hide Claude's answer.
        try {
            repository.recordUsage(model, input, output)
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
            // Usage stats are nice-to-have.
        }
    }

    /** Runs [block], turning SDK/network exceptions into friendly [AiError]s. */
    private suspend fun <T> aiCall(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw mapError(e)
    }

    private fun Effort.toSdk(): OutputConfig.Effort = when (this) {
        Effort.LOW -> OutputConfig.Effort.LOW
        Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
        Effort.HIGH -> OutputConfig.Effort.HIGH
    }

    companion object {
        /** Beta flag for `fallbacks: "default"` (server-side refusal fallback). */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        private val foodSchema: JsonOutputFormat.Schema by lazy {
            val builder = JsonOutputFormat.Schema.builder()
            FoodAnalysisSchema.schema.forEach { (key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)) }
            builder.build()
        }

        /** Maps SDK exceptions (typed classes, no string matching) to the app's [AiError]s. */
        fun mapError(e: Throwable): AiError = when (e) {
            is AiError -> e
            is UnauthorizedException, is PermissionDeniedException -> AiError.InvalidKey()
            is RateLimitException -> AiError.RateLimited()
            is AnthropicIoException, is IOException -> AiError.Network()
            is InternalServerException ->
                AiError.Other("Claude's servers are busy right now. Please try again in a minute!")
            is BadRequestException ->
                AiError.Other("Claude couldn't process that request. Try again, or switch the model mode in Settings.")
            is AnthropicServiceException ->
                AiError.Other("Claude had a little hiccup (error ${e.statusCode()}). Please try again!")
            else -> {
                // The SDK sometimes wraps network failures; look one level down.
                val cause = e.cause
                if (cause is IOException) AiError.Network()
                else AiError.Other("Something went wrong talking to Claude. Please try again!")
            }
        }
    }
}

package net.perfectdreams.loritta.cinnamon.platform.commands

import dev.kord.common.entity.DiscordAttachment
import dev.kord.common.entity.Snowflake
import dev.kord.rest.Image
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging
import net.perfectdreams.discordinteraktions.common.commands.ApplicationCommandContext
import net.perfectdreams.discordinteraktions.common.commands.GuildApplicationCommandContext
import net.perfectdreams.discordinteraktions.common.commands.options.SlashCommandArguments
import net.perfectdreams.discordinteraktions.common.entities.Icon
import net.perfectdreams.discordinteraktions.common.entities.User
import net.perfectdreams.discordinteraktions.common.requests.InteractionRequestState
import net.perfectdreams.i18nhelper.core.I18nContext
import net.perfectdreams.loritta.cinnamon.common.commands.ApplicationCommandType
import net.perfectdreams.loritta.cinnamon.common.emotes.Emotes
import net.perfectdreams.loritta.cinnamon.common.images.ImageReference
import net.perfectdreams.loritta.cinnamon.common.images.URLImageReference
import net.perfectdreams.loritta.cinnamon.common.utils.GACampaigns
import net.perfectdreams.loritta.cinnamon.common.utils.PendingImportantNotificationState
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.options.ChannelCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.CommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.ImageReferenceCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.NullableChannelCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.NullableCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.NullableRoleCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.NullableUserCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.RoleCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.StringListCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.UserCommandOption
import net.perfectdreams.loritta.cinnamon.platform.commands.options.UserListCommandOption
import net.perfectdreams.loritta.cinnamon.platform.utils.ContextStringToUserInfoConverter
import net.perfectdreams.loritta.cinnamon.platform.utils.ImportantNotificationDatabaseMessage
import net.perfectdreams.loritta.cinnamon.platform.utils.metrics.Prometheus
import net.perfectdreams.loritta.cinnamon.pudding.data.ServerConfigRoot
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import kotlin.streams.toList
import net.perfectdreams.loritta.cinnamon.platform.commands.ApplicationCommandContext as CinnamonApplicationCommandContext
import net.perfectdreams.loritta.cinnamon.platform.commands.GuildApplicationCommandContext as CinnamonGuildApplicationCommandContext

/**
 * Bridge between Cinnamon's [SlashCommandExecutor] and Discord InteraKTions' [SlashCommandExecutor].
 *
 * Used for argument conversion between the two platforms
 */
class SlashCommandExecutorWrapper(
    private val loritta: LorittaCinnamon,
    // This is only used for metrics and logs
    private val rootDeclaration: SlashCommandDeclarationBuilder,
    private val declarationExecutor: SlashCommandExecutorDeclaration,
    private val executor: SlashCommandExecutor
) : net.perfectdreams.discordinteraktions.common.commands.SlashCommandExecutor() {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val SUPPORTED_IMAGE_EXTENSIONS = listOf(
            "png",
            "jpg",
            "jpeg",
            "bmp",
            "tiff",
            "gif"
        )

        val NonGuildServerConfigRoot = ServerConfigRoot(0u, "pt")
    }

    override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
        val stringifiedArgumentNames = stringifyArgumentNames(args.types)
        val rootDeclarationClazzName = rootDeclaration.parent.simpleName
        val executorClazzName = executor::class.simpleName

        logger.info { "(${context.sender.id.value}) $executor $stringifiedArgumentNames" }

        val timer = Prometheus.EXECUTED_COMMAND_LATENCY_COUNT
            .labels(rootDeclarationClazzName, executorClazzName)
            .startTimer()

        // Map Cinnamon Arguments to Discord InteraKTions Arguments
        val cinnamonArgs = mutableMapOf<CommandOption<*>, Any?>()
        val guildId = (context as? GuildApplicationCommandContext)?.guildId

        val result = executeCommand(
            rootDeclarationClazzName,
            executorClazzName,
            context,
            args,
            cinnamonArgs,
            guildId
        )

        var stacktrace: String? = null
        if (result is CommandExecutionFailure)
            stacktrace = result.throwable.stackTraceToString()

        val commandLatency = timer.observeDuration()
        logger.info { "(${context.sender.id.value}) $executor $stringifiedArgumentNames - OK! Result: ${result}; Took ${commandLatency * 1000}ms" }

        loritta.services.executedInteractionsLog.insertApplicationCommandLog(
            context.sender.id.value.toLong(),
            guildId?.value?.toLong(),
            context.channelId.value.toLong(),
            Clock.System.now(),
            ApplicationCommandType.CHAT_INPUT,
            rootDeclarationClazzName!!,
            executorClazzName!!,
            buildJsonWithArguments(cinnamonArgs),
            stacktrace == null,
            commandLatency,
            stacktrace
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun executeCommand(
        rootDeclarationClazzName: String?,
        executorClazzName: String?,
        context: ApplicationCommandContext,
        args: SlashCommandArguments,
        cinnamonArgs: MutableMap<CommandOption<*>, Any?>,
        guildId: Snowflake?
    ): CommandExecutionResult {
        // These variables are used in the catch { ... } block, to make our lives easier
        var i18nContext: I18nContext? = null
        val cinnamonContext: CinnamonApplicationCommandContext?

        val interaKTionsArgumentEntries = args.types.entries

        try {
            val serverConfig = if (guildId != null) {
                // TODO: Fix this workaround, while this does work, it isn't that good
                loritta.services.serverConfigs.getServerConfigRoot(guildId.value)?.data ?: NonGuildServerConfigRoot
            } else {
                // TODO: Should this class *really* be named "ServerConfig"? After all, it isn't always used for guilds
                NonGuildServerConfigRoot
            }

            // Patches and workarounds!!!
            val localeId = when (serverConfig.localeId) {
                "default" -> "pt"
                "en-us" -> "en"
                else -> serverConfig.localeId
            }

            i18nContext = loritta.languageManager.getI18nContextById(localeId)

            cinnamonContext = if (context is GuildApplicationCommandContext) {
                CinnamonGuildApplicationCommandContext(
                    loritta,
                    i18nContext,
                    context.sender,
                    context,
                    context.guildId,
                    context.member
                )
            } else {
                CinnamonApplicationCommandContext(
                    loritta,
                    i18nContext,
                    context.sender,
                    context
                )
            }

            // Don't let users that are banned from using Loritta
            if (handleIfBanned(cinnamonContext))
                return CommandExecutionSuccess

            if (!rootDeclaration.allowedInPrivateChannel && guildId == null) {
                TODO()
                /* context.sendEphemeralMessage {
                    content = ":no_entry: **|** ${locale["commands.cantUseInPrivate"]}"
                } */
                return CommandExecutionSuccess
            }

            declarationExecutor.options.arguments.forEach {
                when (it) {
                    is StringListCommandOption -> {
                        // Special case: Lists
                        val listsValues = interaKTionsArgumentEntries.filter { opt -> opt.key.name.startsWith(it.name) }
                        cinnamonArgs[it] = mutableListOf<String>().also {
                            it.addAll(listsValues.map { it.value as String })
                        }
                    }

                    is UserListCommandOption -> {
                        val listsValues = interaKTionsArgumentEntries.filter { opt -> opt.key.name.startsWith(it.name) }
                        cinnamonArgs[it] = mutableListOf<User>().also {
                            it.addAll(listsValues.map { it.value as User })
                        }
                    }

                    is ImageReferenceCommandOption -> {
                        // Special case: Image References
                        // Get the argument that matches our image reference
                        val interaKTionAttachmentArgument = interaKTionsArgumentEntries.firstOrNull { opt -> opt.key.name.removeSuffix("_file") == it.name }
                        val interaKTionAvatarLinkOrEmoteArgument = interaKTionsArgumentEntries.firstOrNull { opt -> opt.key.name.removeSuffix("_data") == it.name }

                        var found = false

                        // Attachments take priority
                        if (interaKTionAttachmentArgument != null) {
                            val attachment = (interaKTionAttachmentArgument.value as DiscordAttachment)
                            if (attachment.filename.substringAfterLast(".").lowercase() in SUPPORTED_IMAGE_EXTENSIONS) {
                                found = true
                                cinnamonArgs[it] =  URLImageReference(attachment.url)
                            }
                        } else if (interaKTionAvatarLinkOrEmoteArgument != null) {
                            val value = interaKTionAvatarLinkOrEmoteArgument.value as String

                            // Now check if it is a valid thing!
                            // First, we will try matching via user mentions or user IDs
                            val cachedUserInfo = ContextStringToUserInfoConverter.convert(
                                cinnamonContext,
                                value
                            )

                            if (cachedUserInfo != null) {
                                val icon = cachedUserInfo.avatarId?.let {
                                    Icon.UserAvatar(
                                        Snowflake(cachedUserInfo.id.value),
                                        it
                                    )
                                } ?: Icon.DefaultUserAvatar(cachedUserInfo.discriminator.toInt())

                                cinnamonArgs[it] = URLImageReference(icon.cdnUrl.toUrl {
                                    this.format = Image.Format.PNG
                                    this.size = Image.Size.Size128
                                })
                                found = true
                            }

                            if (!found && value.startsWith("http")) {
                                // It is a URL!
                                // TODO: Use a RegEx to check if it is a valid URL
                                cinnamonArgs[it] = URLImageReference(value)
                                found = true
                            }

                            if (!found) {
                                // It is a emote!
                                // Discord emotes always starts with "<" and ends with ">"
                                if (value.startsWith("<") && value.endsWith(">")) {
                                    val emoteId = value.substringAfterLast(":").substringBefore(">")
                                    cinnamonArgs[it] =
                                        URLImageReference("https://cdn.discordapp.com/emojis/${emoteId}.png?v=1")
                                } else {
                                    // If not, we are going to handle it as if it were a Unicode emoji
                                    val emoteId = value.codePoints().toList()
                                        .joinToString(separator = "-") { String.format("\\u%04x", it).substring(2) }
                                    cinnamonArgs[it] =
                                        URLImageReference("https://twemoji.maxcdn.com/2/72x72/$emoteId.png")
                                }
                                found = true
                            }
                        }

                        if (!found) {
                            // If no image was found, we will try to find the first recent message in this chat
                            val channelId = context.channelId
                            val messages = loritta.rest.channel.getMessages(
                                channelId,
                                null,
                                100
                            )
                            try {
                                // Sort from the newest message to the oldest message
                                val attachmentUrl = messages.sortedByDescending { it.id.timestamp }
                                    .flatMap { it.attachments }
                                    .firstOrNull {
                                        // Only get filenames ending with "image" extensions
                                        it.filename.substringAfter(".")
                                            .lowercase() in SUPPORTED_IMAGE_EXTENSIONS
                                    }?.url

                                if (attachmentUrl != null) {
                                    // Found a valid URL, let's go!
                                    cinnamonArgs[it] = URLImageReference(attachmentUrl)
                                    found = true
                                }
                            } catch (e: Exception) {
                                // TODO: Catch the "permission required" exception and show a nice message
                                e.printStackTrace()
                            }
                        }

                        if (!found)
                            cinnamonContext.fail(cinnamonContext.i18nContext.get(I18nKeysData.Commands.NoValidImageFound), Emotes.LoriSob)
                    }

                    else -> {
                        val interaKTionArgument = interaKTionsArgumentEntries.firstOrNull { opt -> it.name == opt.key.name }

                        // If the value is null but it *wasn't* meant to be null, we are going to throw a exception!
                        // (This should NEVER happen!)
                        if (interaKTionArgument?.value == null && it !is NullableCommandOption)
                            throw UnsupportedOperationException("Argument ${interaKTionArgument?.key} value is null, but the type of the argument is not nullable! Bug?")

                        when (it) {
                            is UserCommandOption, is NullableUserCommandOption -> {
                                cinnamonArgs[it] = interaKTionArgument?.value?.let {
                                    interaKTionArgument.value
                                }
                            }

                            is ChannelCommandOption, is NullableChannelCommandOption -> {
                                cinnamonArgs[it] = interaKTionArgument?.value?.let {
                                    interaKTionArgument.value
                                }
                            }

                            is RoleCommandOption, is NullableRoleCommandOption -> {
                                cinnamonArgs[it] = interaKTionArgument?.value?.let {
                                    interaKTionArgument.value
                                }
                            }

                            else -> {
                                cinnamonArgs[it] = interaKTionArgument?.value
                            }
                        }
                    }
                }
            }

            // TODO: Don't use GlobalScope!
            GlobalScope.launch {
                // Update the cached Discord users
                // Updating in a separate task to avoid delaying the command processing too much
                val users = mutableSetOf(context.sender)
                users.addAll(args.types.values.filterIsInstance<User>())
                val resolvedUsers = context.data.resolved?.users?.values
                if (resolvedUsers != null)
                // TODO: Maybe implement proper hash codes in the InteraKTions "User"?
                    users.addAll(resolvedUsers.distinctBy { it.id })
                val jobs = users
                    .map {
                        async {
                            loritta.insertOrUpdateCachedUserInfo(it)
                        }
                    }

                jobs.awaitAll()

                logger.info { "Successfully updated user info cache of ${jobs.size} users!" }
            }

            executor.execute(
                cinnamonContext,
                net.perfectdreams.loritta.cinnamon.platform.commands.options.SlashCommandArguments(cinnamonArgs)
            )

            // Required because "Smart cast is impossible" within the scope
            val localI18nContext = i18nContext
            // Additional messages that must be sent after the command sends at least one message
            // TODO: Don't use GlobalScope!!
            GlobalScope.launch {
                var state = context.bridge.state.value

                try {
                    withTimeout(15_000) {
                        while (state != InteractionRequestState.ALREADY_REPLIED)
                            state = context.bridge.state.awaitChange() // The ".awaitChange()" is cancellable
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warn(e) { "Timed out while waiting for InteractionRequestState, we won't send any additional messages then..." }
                    return@launch
                }

                // At this point, state should be "ALREADY_REPLIED"
                val userId = UserId(context.sender.id.value)

                // Website Update Message
                val patchNotesNotifications = loritta.services.patchNotesNotifications.getUnreadPatchNotesNotificationsAndMarkAsRead(
                    UserId(context.sender.id.value),
                    Clock.System.now()
                )

                for (patchNote in patchNotesNotifications) {
                    context.sendEphemeralMessage {
                        styled(
                            localI18nContext.get(
                                I18nKeysData.Commands.CheckOutNews(
                                    GACampaigns.patchNotesUrl(
                                        loritta.config.website,
                                        localI18nContext.get(I18nKeysData.Website.LocalePathId),
                                        patchNote.path,
                                        "discord",
                                        "slash-commands",
                                        "lori-news",
                                        "patch-notes-notification"
                                    )
                                )
                            ),
                            Emotes.LoriSunglasses
                        )
                    }
                }

                // Pending Daily Tax Direct Message
                val pendingDailyTaxDirectMessage = loritta.services.users.getAndUpdateImportantNotificationsState(
                    userId,
                    listOf(
                        PendingImportantNotificationState.PENDING,
                        PendingImportantNotificationState.FAILED_TO_SEND_VIA_DIRECT_MESSAGE,
                        PendingImportantNotificationState.SKIPPED_DIRECT_MESSAGE
                    ),
                    PendingImportantNotificationState.SUCCESSFULLY_SENT_VIA_EPHEMERAL_MESSAGE
                )

                if (pendingDailyTaxDirectMessage != null) {
                    val builder = Json.decodeFromString<ImportantNotificationDatabaseMessage>(pendingDailyTaxDirectMessage)

                    // Skip the stuff, just send it directly
                    cinnamonContext.interaKTionsContext.sendEphemeralMessage(builder.toEphemeralInteractionOrFollowupMessageCreateBuilder())
                }
            }

            return CommandExecutionSuccess
        } catch (e: Throwable) {
            if (e is SilentCommandException)
                return CommandExecutionSuccess // SilentCommandExceptions should be ignored

            if (e is CommandException) {
                context.sendPublicMessage(e.builder)
                return CommandExecutionSuccess
            }

            if (e is EphemeralCommandException) {
                context.sendEphemeralMessage(e.builder)
                return CommandExecutionSuccess
            }

            logger.warn(e) { "Something went wrong while executing $rootDeclarationClazzName $executorClazzName" }

            // If the i18nContext is not present, we will default to the default language provided
            i18nContext = i18nContext ?: loritta.languageManager.getI18nContextById(loritta.languageManager.defaultLanguageId)

            // Tell the user that something went *really* wrong
            // While we do have access to the Cinnamon Context, it may be null at this stage, so we will use the Discord InteraKTions context
            val content = "${Emotes.LoriHm} **|** " + i18nContext.get(
                I18nKeysData.Commands.ErrorWhileExecutingCommand(
                    loriRage = Emotes.LoriRage,
                    loriSob = Emotes.LoriSob,
                    stacktrace = if (!e.message.isNullOrEmpty())
                        " `${e.message}`" // TODO: Sanitize
                    else
                        " `${e::class.simpleName}`"
                )
            )

            // If the context was already deferred, but it isn't ephemeral, then we will send a non-ephemeral message
            val isEphemeral = if (context.isDeferred)
                context.wasInitiallyDeferredEphemerally
            else true

            if (isEphemeral)
                context.sendEphemeralMessage {
                    this.content = content
                }
            else
                context.sendMessage {
                    this.content = content
                }

            return CommandExecutionFailure(e)
        }
    }

    private suspend fun handleIfBanned(context: CinnamonApplicationCommandContext): Boolean {
        // Check if the user is banned from using Loritta
        val userBannedState = loritta.services.users.getUserBannedState(UserId(context.user.id.value))

        if (userBannedState != null) {
            val banDateInEpochSeconds = userBannedState.bannedAt.epochSeconds
            val expiresDateInEpochSeconds = userBannedState.expiresAt?.epochSeconds

            context.sendEphemeralMessage {
                val banAppealPageUrl = loritta.config.website + "extras/faq-loritta/loritta-ban-appeal"
                content = context.i18nContext.get(
                    if (expiresDateInEpochSeconds != null) {
                        I18nKeysData.Commands.YouAreLorittaBannedTemporary(
                            loriHmpf = Emotes.LoriHmpf,
                            reason = userBannedState.reason,
                            banDate = "<t:$banDateInEpochSeconds:R> (<t:$banDateInEpochSeconds:f>)",
                            expiresDate = "<t:$expiresDateInEpochSeconds:R> (<t:$expiresDateInEpochSeconds:f>)",
                            banAppealPageUrl = banAppealPageUrl,
                            loriAmeno = Emotes.loriAmeno,
                            loriSob = Emotes.LoriSob
                        )
                    } else {
                        I18nKeysData.Commands.YouAreLorittaBannedPermanent(
                            loriHmpf = Emotes.LoriHmpf,
                            reason = userBannedState.reason,
                            banDate = "<t:$banDateInEpochSeconds:R> (<t:$banDateInEpochSeconds:f>)",
                            banAppealPageUrl = banAppealPageUrl,
                            loriAmeno = Emotes.loriAmeno,
                            loriSob = Emotes.LoriSob
                        )
                    }

                ).joinToString("\n")
            }
            return true
        }
        return false
    }

    sealed class CommandExecutionResult
    object CommandExecutionSuccess : CommandExecutionResult()
    class CommandExecutionFailure(val throwable: Throwable) : CommandExecutionResult()

    override fun signature() = declarationExecutor::class

    /**
     * Stringifies the arguments in the [types] map to its name
     *
     * Useful for debug logging
     *
     * @param types the arguments
     * @return a map with argument name -> argument value
     */
    private fun stringifyArgumentNames(types: Map<net.perfectdreams.discordinteraktions.common.commands.options.CommandOption<*>, Any?>) = types.map { it.key.name to it.value }
        .toMap()

    private fun buildJsonWithArguments(types: Map<CommandOption<*>, Any?>) = buildJsonObject {
        for ((option, value) in types) {
            when (value) {
                is ImageReference -> put(option.name, value.url)
                is Number -> put(option.name, value)
                else -> put(option.name, value.toString())
            }
        }
    }
}
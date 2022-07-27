package net.perfectdreams.loritta.cinnamon.platform.commands.`fun`

import net.perfectdreams.discordinteraktions.common.builder.message.embed
import net.perfectdreams.discordinteraktions.common.utils.author
import net.perfectdreams.loritta.cinnamon.platform.commands.ApplicationCommandContext
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandExecutor
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.`fun`.declarations.VieirinhaCommand
import net.perfectdreams.loritta.cinnamon.platform.commands.options.LocalizedApplicationCommandOptions
import net.perfectdreams.discordinteraktions.common.commands.options.SlashCommandArguments

class VieirinhaExecutor(loritta: LorittaCinnamon) : CinnamonSlashCommandExecutor(loritta) {
    inner class Options : LocalizedApplicationCommandOptions(loritta) {
        // Unused because... well, we don't need it :P
        val question = string("question", VieirinhaCommand.I18N_PREFIX.Options.Question)
    }

    override val options = Options()

    override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
        context.sendMessage {
            embed {
                author("Vieirinha", null, "http://i.imgur.com/rRtHdti.png")

                description = context.i18nContext.get(
                    VieirinhaCommand.I18N_PREFIX.Responses(
                        VieirinhaCommand.PUNCTUATIONS.random()
                    )
                ).random()
            }
        }
    }
}
package net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.`fun`.declarations

import net.perfectdreams.loritta.cinnamon.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.discord.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.CinnamonSlashCommandDeclarationWrapper
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.`fun`.CancelledExecutor

class CancelledCommand(languageManager: LanguageManager) : CinnamonSlashCommandDeclarationWrapper(languageManager) {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Cancelled
    }

    override fun declaration() = slashCommand(I18N_PREFIX.Label, CommandCategory.FUN, I18N_PREFIX.Description) {
        executor = { CancelledExecutor(it) }
    }
}
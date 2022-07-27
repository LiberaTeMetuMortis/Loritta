package net.perfectdreams.loritta.cinnamon.platform.commands.utils.declarations

import net.perfectdreams.loritta.cinnamon.common.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.common.utils.TodoFixThisData
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandDeclarationWrapper
import net.perfectdreams.loritta.cinnamon.platform.commands.utils.packtracker.PackageListExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.utils.packtracker.TrackPackageExecutor
import net.perfectdreams.loritta.cinnamon.platform.utils.correios.CorreiosClient

class PackageCommand(languageManager: LanguageManager) : CinnamonSlashCommandDeclarationWrapper(languageManager) {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Package
    }

    override fun declaration() = slashCommand("package", CommandCategory.UTILS, TodoFixThisData) {
        subcommand("track", I18N_PREFIX.Track.Description) {
            executor = { TrackPackageExecutor(it, it.correiosClient) }
        }

        subcommand("list", I18N_PREFIX.List.Description) {
            executor = { PackageListExecutor(it, it.correiosClient) }
        }
    }
}
package net.perfectdreams.loritta.cinnamon.platform.commands.images.declarations

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.common.utils.TodoFixThisData
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandDeclarationWrapper
import net.perfectdreams.loritta.cinnamon.platform.commands.images.KnuxThrowExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.images.ManiaTitleCardExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.images.StudiopolisTvExecutor

class SonicCommand(loritta: LorittaCinnamon, val gabiClient: GabrielaImageServerClient) : CinnamonSlashCommandDeclarationWrapper(loritta) {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Sonic
    }

    override fun declaration() = slashCommand("sonic", CommandCategory.IMAGES, TodoFixThisData) {
        subcommand(
            "knuxthrow",
            I18N_PREFIX.Knuxthrow
                .Description
        ) {
            executor = KnuxThrowExecutor(loritta, gabiClient)
        }

        subcommand("maniatitlecard", I18N_PREFIX.Maniatitlecard.Description) {
            executor = ManiaTitleCardExecutor(loritta, gabiClient)
        }

        subcommand("studiopolistv", I18N_PREFIX.Studiopolistv.Description) {
            executor = StudiopolisTvExecutor(loritta, gabiClient)
        }
    }
}
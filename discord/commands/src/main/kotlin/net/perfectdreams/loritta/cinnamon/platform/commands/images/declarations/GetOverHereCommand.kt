package net.perfectdreams.loritta.cinnamon.platform.commands.images.declarations

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandDeclarationWrapper
import net.perfectdreams.loritta.cinnamon.platform.commands.images.GetOverHereExecutor

class GetOverHereCommand(loritta: LorittaCinnamon, val gabiClient: GabrielaImageServerClient) : CinnamonSlashCommandDeclarationWrapper(loritta) {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Getoverhere
    }

    override fun declaration() = slashCommand("getoverhere", CommandCategory.IMAGES, I18N_PREFIX.Description) {
        executor = GetOverHereExecutor(loritta, gabiClient)
    }
}
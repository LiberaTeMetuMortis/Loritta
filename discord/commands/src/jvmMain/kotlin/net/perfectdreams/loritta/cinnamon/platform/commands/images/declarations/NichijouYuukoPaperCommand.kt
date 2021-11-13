package net.perfectdreams.loritta.cinnamon.platform.commands.images.declarations

import net.perfectdreams.loritta.cinnamon.platform.commands.images.NichijouYuukoPaperExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.platform.commands.declarations.CommandDeclaration
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData

object NichijouYuukoPaperCommand : CommandDeclaration {
    val I18N_PREFIX = I18nKeysData.Commands.Command.Discordping

    override fun declaration() = command(listOf("discordping", "discórdia", "discordia"), CommandCategory.IMAGES, I18N_PREFIX.Description) {
        executor = NichijouYuukoPaperExecutor
    }
}
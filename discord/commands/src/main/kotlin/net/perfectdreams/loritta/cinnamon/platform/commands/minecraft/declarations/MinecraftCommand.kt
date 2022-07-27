package net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.declarations

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.common.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.CommandCategory
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandDeclarationWrapper

import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McAvatarExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McBodyExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McHeadExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McOfflineUUIDExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McSkinExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McSkinLorittaSweatshirtExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.minecraft.McUUIDExecutor
import net.perfectdreams.minecraftmojangapi.MinecraftMojangAPI

class MinecraftCommand(languageManager: LanguageManager) : CinnamonSlashCommandDeclarationWrapper(languageManager) {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Minecraft
        val I18N_CATEGORY_PREFIX = I18nKeysData.Commands.Category.Minecraft
    }

    override fun declaration() = slashCommand("minecraft", CommandCategory.MINECRAFT, I18N_CATEGORY_PREFIX.Name /* TODO: Use the category description */) {
        subcommandGroup("player", I18N_PREFIX.Player.Description) {
            subcommand("skin", I18N_PREFIX.Player.Skin.Description) {
                executor = { McSkinExecutor(it, it.mojangApi) }
            }

            subcommand("avatar", I18N_PREFIX.Player.Avatar.Description) {
                executor = { McAvatarExecutor(it, it.mojangApi) }
            }

            subcommand("head", I18N_PREFIX.Player.Head.Description) {
                executor = { McHeadExecutor(it, it.mojangApi) }
            }

            subcommand("body", I18N_PREFIX.Player.Body.Description) {
                executor = { McBodyExecutor(it, it.mojangApi) }
            }

            subcommand("onlineuuid", I18N_PREFIX.Player.Onlineuuid.Description) {
                executor = { McUUIDExecutor(it, it.mojangApi) }
            }

            subcommand("offlineuuid", I18N_PREFIX.Player.Offlineuuid.Description) {
                executor = { McOfflineUUIDExecutor(it) }
            }
        }

        subcommand("sweatshirt", I18N_PREFIX.Sweatshirt.Description) {
            executor = { McSkinLorittaSweatshirtExecutor(it, it.gabrielaImageServerClient, it.mojangApi) }
        }
    }
}
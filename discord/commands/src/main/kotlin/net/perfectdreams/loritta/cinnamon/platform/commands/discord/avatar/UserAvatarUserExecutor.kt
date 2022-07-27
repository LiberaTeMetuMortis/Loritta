package net.perfectdreams.loritta.cinnamon.platform.commands.discord.avatar

import dev.kord.common.entity.Snowflake
import net.perfectdreams.discordinteraktions.common.entities.InteractionMember
import net.perfectdreams.discordinteraktions.common.entities.User
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.ApplicationCommandContext
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonUserCommandExecutor

class UserAvatarUserExecutor(loritta: LorittaCinnamon) : CinnamonUserCommandExecutor(loritta), UserAvatarExecutor {
    override suspend fun execute(
        context: ApplicationCommandContext,
        targetUser: User,
        targetMember: InteractionMember?
    ) {
        handleAvatarCommand(context, applicationId, targetUser, targetMember, true)
    }
}
package net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.discord.info

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import io.ktor.client.*
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.ApplicationCommandContext
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.CinnamonUserCommandExecutor

class UserInfoUserExecutor(loritta: LorittaBot, override val http: HttpClient) : CinnamonUserCommandExecutor(loritta), UserInfoExecutor {
    override suspend fun execute(
        context: ApplicationCommandContext,
        targetUser: User,
        targetMember: Member?
    ) {
        handleUserExecutor(
            context,
            targetUser,
            targetMember,
            true
        )
    }
}
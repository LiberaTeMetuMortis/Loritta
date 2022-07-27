package net.perfectdreams.loritta.cinnamon.platform.commands.roleplay

import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.randomroleplaypictures.client.RandomRoleplayPicturesClient

class RoleplayDanceExecutor(
    loritta: LorittaCinnamon,
    client: RandomRoleplayPicturesClient,
) : RoleplayPictureExecutor(
    loritta,
    client,
    RoleplayUtils.DANCE_ATTRIBUTES
)
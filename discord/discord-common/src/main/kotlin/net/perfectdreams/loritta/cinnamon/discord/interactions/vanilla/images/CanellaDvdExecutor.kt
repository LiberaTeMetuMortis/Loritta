package net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.discord.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images.base.GabrielaImageServerSingleCommandBase
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images.base.SingleImageOptions

class CanellaDvdExecutor(
    loritta: LorittaCinnamon,
    client: GabrielaImageServerClient
) : GabrielaImageServerSingleCommandBase(
    loritta,
    client,
    { client.images.canellaDvd(it) },
    "canella_dvd.png"
)
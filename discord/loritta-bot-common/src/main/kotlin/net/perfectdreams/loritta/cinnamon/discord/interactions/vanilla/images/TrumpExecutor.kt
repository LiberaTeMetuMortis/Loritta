package net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images.base.GabrielaImageServerTwoCommandBase
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.images.base.TwoImagesOptions

class TrumpExecutor(
    loritta: LorittaBot,
    client: GabrielaImageServerClient
) : GabrielaImageServerTwoCommandBase(
    loritta,
    client,
    { client.images.trump(it) },
    "trump.gif"
)
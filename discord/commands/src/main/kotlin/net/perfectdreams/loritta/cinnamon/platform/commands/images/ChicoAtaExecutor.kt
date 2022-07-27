package net.perfectdreams.loritta.cinnamon.platform.commands.images

import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.images.base.GabrielaImageServerSingleCommandBase
import net.perfectdreams.loritta.cinnamon.platform.commands.images.base.SingleImageOptions

class ChicoAtaExecutor(
    loritta: LorittaCinnamon,
    client: GabrielaImageServerClient
) : GabrielaImageServerSingleCommandBase(
    loritta,
    client,
    { client.images.chicoAta(it) },
    "chico_ata.png"
)
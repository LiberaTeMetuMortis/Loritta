package net.perfectdreams.loritta.morenitta.tables

import net.perfectdreams.loritta.cinnamon.pudding.tables.UserSettings
import org.jetbrains.exposed.dao.id.LongIdTable

object CustomBackgroundSettings : LongIdTable() {
    val settings = reference("settings", UserSettings).uniqueIndex() // We only want one reference to a settings entry here, that's why it is unique
    val file = text("file")
    val preferredMediaType = text("preferred_media_type")
}
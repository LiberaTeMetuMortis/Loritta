package net.perfectdreams.loritta.cinnamon.pudding.tables

/**
 * Stores a Guild ID -> Hash for Loritta's application commands
 *
 * If it is a global Loritta instance, the Guild ID will be set to 0.
 *
 * Useful to track if commands needs to be upserted or not!
 */
object DiscordLorittaApplicationCommandHashes : SnowflakeTable() {
    val hash = long("hash")
}
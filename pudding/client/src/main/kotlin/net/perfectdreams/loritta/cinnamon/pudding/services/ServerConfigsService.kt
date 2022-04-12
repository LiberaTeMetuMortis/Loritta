package net.perfectdreams.loritta.cinnamon.pudding.services

import net.perfectdreams.loritta.cinnamon.pudding.Pudding
import net.perfectdreams.loritta.cinnamon.pudding.data.MiscellaneousConfig
import net.perfectdreams.loritta.cinnamon.pudding.data.StarboardConfig
import net.perfectdreams.loritta.cinnamon.pudding.entities.PuddingServerConfigRoot
import net.perfectdreams.loritta.cinnamon.pudding.tables.MiscellaneousConfigs
import net.perfectdreams.loritta.cinnamon.pudding.tables.ServerConfigs
import net.perfectdreams.loritta.cinnamon.pudding.tables.StarboardConfigs
import org.jetbrains.exposed.sql.select

class ServerConfigsService(private val pudding: Pudding) : Service(pudding) {
    suspend fun getServerConfigRoot(id: ULong): PuddingServerConfigRoot? {
        return pudding.transaction {
            ServerConfigs.select {
                ServerConfigs.id eq id.toLong()
            }.firstOrNull()
        }?.let { PuddingServerConfigRoot.fromRow(it) }
    }

    suspend fun getStarboardConfigById(id: Long): StarboardConfig? {
        return pudding.transaction {
            StarboardConfigs.select {
                StarboardConfigs.id eq id
            }.firstOrNull()
        }?.let { StarboardConfig.fromRow(it) }
    }

    suspend fun getMiscellaneousConfigById(id: Long): MiscellaneousConfig? {
        return pudding.transaction {
            MiscellaneousConfigs.select {
                MiscellaneousConfigs.id eq id
            }.firstOrNull()
        }?.let { MiscellaneousConfig.fromRow(it) }
    }
}
package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.modules

import dev.kord.common.entity.DiscordAddedGuildMember
import dev.kord.common.entity.DiscordChannel
import dev.kord.common.entity.DiscordGuild
import dev.kord.common.entity.DiscordGuildMember
import dev.kord.common.entity.DiscordRemovedGuildMember
import dev.kord.common.entity.DiscordRole
import dev.kord.common.entity.DiscordUpdatedGuildMember
import dev.kord.common.entity.Overwrite
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.orEmpty
import dev.kord.common.entity.optional.value
import dev.kord.gateway.ChannelCreate
import dev.kord.gateway.ChannelDelete
import dev.kord.gateway.ChannelUpdate
import dev.kord.gateway.Event
import dev.kord.gateway.GuildCreate
import dev.kord.gateway.GuildDelete
import dev.kord.gateway.GuildMemberAdd
import dev.kord.gateway.GuildMemberRemove
import dev.kord.gateway.GuildMemberUpdate
import dev.kord.gateway.GuildRoleCreate
import dev.kord.gateway.GuildRoleDelete
import dev.kord.gateway.GuildRoleUpdate
import dev.kord.gateway.GuildUpdate
import dev.kord.gateway.MessageCreate
import kotlinx.datetime.toJavaInstant
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.DiscordGatewayEventsProcessor
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.batchUpsert
import net.perfectdreams.loritta.cinnamon.platform.utils.toLong
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildChannelPermissionOverrides
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildChannels
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildMemberRoles
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildMembers
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildRoles
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuilds
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import pw.forst.exposed.insertOrUpdate

class DiscordCacheModule(private val m: DiscordGatewayEventsProcessor) : ProcessDiscordEventsModule() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun processEvent(event: Event) {
        when (event) {
            is GuildCreate -> {
                // logger.info { "Howdy ${event.guild.id} (${event.guild.name})! Is unavailable? ${event.guild.unavailable}" }

                if (!event.guild.unavailable.discordBoolean) {
                    val start = System.currentTimeMillis()
                    m.services.transaction {
                        createOrUpdateGuild(event.guild)
                    }
                    logger.info { "GuildCreate took ${System.currentTimeMillis() - start}ms" }
                }
            }
            is GuildUpdate -> {
                if (!event.guild.unavailable.discordBoolean) {
                    m.services.transaction {
                        createOrUpdateGuild(event.guild)
                    }
                }
            }
            is MessageCreate -> {
                val guildId = event.message.guildId.value
                val member = event.message.member.value

                if (guildId != null && member != null) {
                    // Disabled for now, mostly to avoid a lot of database accesses causing a lot of GC pressure
                    /* m.services.transaction {
                        createOrUpdateGuildMember(guildId, event.message.author.id, member)
                    } */
                }
            }
            is GuildMemberAdd -> {
                m.services.transaction {
                    createOrUpdateGuildMember(event.member)
                }
            }
            is GuildMemberUpdate -> {
                m.services.transaction {
                    createOrUpdateGuildMember(event.member)
                }
            }
            is GuildMemberRemove -> {
                m.services.transaction {
                    deleteGuildMember(event.member)
                }
            }
            is ChannelCreate -> {
                val guildId = event.channel.guildId.value
                if (guildId != null)
                    m.services.transaction {
                        createOrUpdateGuildChannel(guildId, event.channel)
                    }
            }
            is ChannelUpdate -> {
                val guildId = event.channel.guildId.value
                if (guildId != null)
                    m.services.transaction {
                        createOrUpdateGuildChannel(guildId, event.channel)
                    }
            }
            is ChannelDelete -> {
                val guildId = event.channel.guildId.value
                if (guildId != null)
                    m.services.transaction {
                        deleteGuildChannel(guildId, event.channel)
                    }
            }
            is GuildRoleCreate -> {
                m.services.transaction {
                    createOrUpdateRole(event.role.guildId, event.role.role)
                }
            }
            is GuildRoleUpdate -> {
                m.services.transaction {
                    createOrUpdateRole(event.role.guildId, event.role.role)
                }
            }
            is GuildRoleDelete -> {
                m.services.transaction {
                    deleteRole(event.role.guildId, event.role.id)
                }
            }
            is GuildDelete -> {
                // If the unavailable field is not set, the user/bot was removed from the guild.
                if (event.guild.unavailable.value == null) {
                    // logger.info { "Someone removed me @ ${event.guild.id}! :(" }
                    m.services.transaction {
                        removeGuildData(event.guild.id)
                    }
                }
            }
            else -> {}
        }
    }

    private fun createOrUpdateGuild(guild: DiscordGuild) {
        DiscordGuilds.insertOrUpdate(DiscordGuilds.id) {
            it[DiscordGuilds.id] = guild.id.value.toLong()
            it[DiscordGuilds.name] = guild.name
            it[DiscordGuilds.icon] = guild.icon
            it[DiscordGuilds.ownerId] = guild.ownerId.value.toLong()

            // joined_at is not null via the Guild Create event
            val joinedAt = guild.joinedAt.value
            if (joinedAt != null)
                it[DiscordGuilds.joinedAt] = joinedAt.toJavaInstant()
        }

        // Shouldn't be null in a GUILD_CREATE event
        val channels = guild.channels.value
        if (channels != null) {
            createOrUpdateAndDeleteGuildChannelsBulk(guild.id, channels)
        }

        createOrUpdateAndDeleteRolesBulk(guild.id, guild.roles)
    }

    private fun createOrUpdateGuildMember(guildMember: DiscordAddedGuildMember) {
        createOrUpdateGuildMember(
            guildMember.guildId,
            guildMember.user.value!!.id,
            guildMember.roles
        )
    }

    private fun createOrUpdateGuildMember(guildMember: DiscordUpdatedGuildMember) {
        createOrUpdateGuildMember(
            guildMember.guildId,
            guildMember.user.id,
            guildMember.roles
        )
    }

    private fun createOrUpdateGuildMember(guildId: Snowflake, userId: Snowflake, guildMember: DiscordGuildMember) {
        createOrUpdateGuildMember(
            guildId,
            userId,
            guildMember.roles
        )
    }

    private fun createOrUpdateGuildMember(
        guildId: Snowflake,
        userId: Snowflake,
        roles: List<Snowflake>
    ) {
        DiscordGuildMembers.insertOrUpdate(DiscordGuildMembers.guildId, DiscordGuildMembers.userId) {
            it[DiscordGuildMembers.guildId] = guildId.toLong()
            it[DiscordGuildMembers.userId] = userId.toLong()
        }

        // This is kinda weird, however we are exchanging the following:
        // 1 statement to do an insert or update
        // *role count* statements to do an insert or update
        // 1 statement to delete removed roles
        //
        // With the following:
        // 1 statement to do an insert or update
        // 1 statement to query roles in the database
        // *amount of missing roles* statements to do an insert or update
        // 1 statement to delete removed roles IF NEEDED
        //
        // Best case scenario? We replace tons of statements with only two (user update + role ID query)!
        // Worst case scenario? It wouldn't be worse compared to the previous implementation ;)
        val roleIdsAsLong = roles.map { it.toLong() }

        val storedRoleIds = DiscordGuildMemberRoles.select {
            (DiscordGuildMemberRoles.guildId eq guildId.toLong()) and
                    (DiscordGuildMemberRoles.userId eq userId.toLong())
        }.toList().map { it[DiscordGuildMemberRoles.roleId] }

        val newRoles = roles.filter { it.toLong() !in storedRoleIds }
        DiscordGuildMemberRoles.batchInsert(newRoles, ignore = true, shouldReturnGeneratedValues = true) { roleId ->
            this[DiscordGuildMemberRoles.guildId] = guildId.toLong()
            this[DiscordGuildMemberRoles.userId] = userId.toLong()
            this[DiscordGuildMemberRoles.roleId] = roleId.toLong()
        }

        val removedRoles = storedRoleIds.filter { it !in roleIdsAsLong }
        DiscordGuildMemberRoles.deleteWhere {
            (DiscordGuildMemberRoles.guildId eq guildId.toLong()) and
                    (DiscordGuildMemberRoles.userId eq userId.toLong()) and
                    (DiscordGuildMemberRoles.roleId inList removedRoles)
        }
    }

    private fun deleteGuildMember(guildMember: DiscordRemovedGuildMember) {
        DiscordGuildMembers.deleteWhere {
            DiscordGuildMembers.guildId eq guildMember.guildId.toLong() and (DiscordGuildMembers.userId eq guildMember.user.id.toLong())
        }

        DiscordGuildMemberRoles.deleteWhere {
            (DiscordGuildMemberRoles.guildId eq guildMember.guildId.toLong()) and
                    (DiscordGuildMemberRoles.userId eq guildMember.user.id.toLong())
        }
    }

    private fun createOrUpdateAndDeleteGuildChannelsBulk(guildId: Snowflake, channels: List<DiscordChannel>) {
        // Create or update all channels
        DiscordGuildChannels.batchUpsertIfNeeded(channels, DiscordGuildChannels.guildId, DiscordGuildChannels.channelId) { it, channel ->
            it[DiscordGuildChannels.guildId] = guildId.toLong()
            it[DiscordGuildChannels.channelId] = channel.id.toLong()
            it[DiscordGuildChannels.name] = channel.name.value
            it[DiscordGuildChannels.type] = channel.type.value

            val permissions = channel.permissions.value
            if (permissions != null) {
                it[DiscordGuildChannels.permissions] = permissions.code.value.toLong()
            }
        }

        updatePermissionOverwrites(guildId, channels)

        // Then delete channels that weren't present in the GuildCreate event
        DiscordGuildChannels.deleteWhere {
            DiscordGuildChannels.guildId eq guildId.toLong() and (DiscordGuildChannels.channelId notInList channels.map { it.id.toLong() })
        }

        cleanUpPermissionOverwrites(guildId, channels)
    }

    private fun createOrUpdateAndDeleteRolesBulk(guildId: Snowflake, roles: List<DiscordRole>) {
        // Create or update all
        DiscordGuildRoles.batchUpsertIfNeeded(
            roles,
            DiscordGuildRoles.guildId,
            DiscordGuildRoles.roleId
        ) { it, role ->
            it[DiscordGuildRoles.guildId] = guildId.toLong()
            it[DiscordGuildRoles.roleId] = role.id.toLong()
            it[DiscordGuildRoles.name] = role.name
            it[DiscordGuildRoles.color] = role.color
            it[DiscordGuildRoles.hoist] = role.hoist
            it[DiscordGuildRoles.icon] = role.icon.value
            it[DiscordGuildRoles.unicodeEmoji] = role.icon.value
            it[DiscordGuildRoles.position] = role.position
            it[DiscordGuildRoles.permissions] = role.permissions.code.value.toLong()
            it[DiscordGuildRoles.managed] = role.managed
            it[DiscordGuildRoles.mentionable] = role.mentionable
        }

        // Then delete roles that weren't present in the GuildCreate event
        DiscordGuildRoles.deleteWhere {
            DiscordGuildRoles.guildId eq guildId.toLong() and (DiscordGuildRoles.roleId notInList roles.map { it.id.toLong() })
        }
    }

    private fun createOrUpdateGuildChannel(guildId: Snowflake, channel: DiscordChannel) {
        DiscordGuildChannels.insertOrUpdate(DiscordGuildChannels.guildId, DiscordGuildChannels.channelId) {
            it[DiscordGuildChannels.guildId] = guildId.toLong()
            it[DiscordGuildChannels.channelId] = channel.id.toLong()
            it[DiscordGuildChannels.name] = channel.name.value
            it[DiscordGuildChannels.type] = channel.type.value

            val permissions = channel.permissions.value
            if (permissions != null) {
                it[DiscordGuildChannels.permissions] = permissions.code.value.toLong()
            }
        }

        updatePermissionOverwrites(guildId, listOf(channel))

        cleanUpPermissionOverwrites(guildId, listOf(channel))
    }

    private fun updatePermissionOverwrites(guildId: Snowflake, channels: List<DiscordChannel>) {
        val overwrites = mutableListOf<Pair<Snowflake, Overwrite>>()

        for (channel in channels) {
            for (permissionOverwrite in channel.permissionOverwrites.orEmpty()) {
                overwrites.add(
                    Pair(
                        channel.id,
                        permissionOverwrite
                    )
                )
            }
        }

        // Create or update all permission overwrites
        DiscordGuildChannelPermissionOverrides.batchUpsertIfNeeded(
            overwrites,
            DiscordGuildChannelPermissionOverrides.guildId,
            DiscordGuildChannelPermissionOverrides.channelId,
            DiscordGuildChannelPermissionOverrides.entityId
        ) { it, channelWithOverwrite ->
            val channelId = channelWithOverwrite.first
            val overwrite = channelWithOverwrite.second

            it[DiscordGuildChannelPermissionOverrides.guildId] = guildId.toLong()
            it[DiscordGuildChannelPermissionOverrides.channelId] = channelId.toLong()
            it[DiscordGuildChannelPermissionOverrides.entityId] = overwrite.id.toLong()
            it[DiscordGuildChannelPermissionOverrides.type] = overwrite.type.value
            it[DiscordGuildChannelPermissionOverrides.allow] = overwrite.allow.code.value.toLong()
            it[DiscordGuildChannelPermissionOverrides.deny] = overwrite.deny.code.value.toLong()
        }
    }

    private fun cleanUpPermissionOverwrites(guildId: Snowflake, channels: List<DiscordChannel>) {
        // Clean up permission overwrites
        val permissionOverwritesOnDatabase = DiscordGuildChannelPermissionOverrides.select {
            (DiscordGuildChannelPermissionOverrides.guildId eq guildId.toLong()) and
                    (DiscordGuildChannelPermissionOverrides.channelId inList channels.map { it.id.toLong() })
        }

        // Hacky!!!
        var cond = Op.build {
            DiscordGuildChannelPermissionOverrides.guildId neq guildId.toLong()
        }

        var shouldCleanUp = false

        for (permissionOverwriteOnDatabase in permissionOverwritesOnDatabase) {
            val pulledChannel = channels.firstOrNull { it.id.toLong() == permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.channelId] }
            if (pulledChannel == null) {
                shouldCleanUp = true
                cond = cond.and {
                    (DiscordGuildChannelPermissionOverrides.channelId eq permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.channelId]) and
                            (DiscordGuildChannelPermissionOverrides.entityId eq permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.entityId])
                }
            } else {
                val pulledOverwrites = pulledChannel.permissionOverwrites.orEmpty().firstOrNull { it.id.toLong() == permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.entityId] }

                if (pulledOverwrites == null) {
                    shouldCleanUp = true
                    cond = cond.and {
                        (DiscordGuildChannelPermissionOverrides.channelId eq permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.channelId]) and
                                (DiscordGuildChannelPermissionOverrides.entityId eq permissionOverwriteOnDatabase[DiscordGuildChannelPermissionOverrides.entityId])
                    }
                }
            }
        }

        if (shouldCleanUp) {
            DiscordGuildChannelPermissionOverrides.deleteWhere {
                DiscordGuildChannelPermissionOverrides.guildId eq guildId.toLong() and cond
            }
        }
    }

    private fun createOrUpdateRole(guildId: Snowflake, role: DiscordRole) = DiscordGuildRoles.insertOrUpdate(DiscordGuildRoles.guildId, DiscordGuildRoles.roleId) {
        it[DiscordGuildRoles.guildId] = guildId.toLong()
        it[DiscordGuildRoles.roleId] = role.id.toLong()
        it[DiscordGuildRoles.name] = role.name
        it[DiscordGuildRoles.color] = role.color
        it[DiscordGuildRoles.hoist] = role.hoist
        it[DiscordGuildRoles.icon] = role.icon.value
        it[DiscordGuildRoles.unicodeEmoji] = role.icon.value
        it[DiscordGuildRoles.position] = role.position
        it[DiscordGuildRoles.permissions] = role.permissions.code.value.toLong()
        it[DiscordGuildRoles.managed] = role.managed
        it[DiscordGuildRoles.mentionable] = role.mentionable
    }

    private fun deleteRole(guildId: Snowflake, roleId: Snowflake) {
        DiscordGuildRoles.deleteWhere {
            (DiscordGuildRoles.guildId eq guildId.toLong()) and (DiscordGuildRoles.roleId eq roleId.toLong())
        }
    }

    private fun deleteGuildChannel(guildId: Snowflake, channel: DiscordChannel) {
        DiscordGuildChannelPermissionOverrides.deleteWhere {
            (DiscordGuildChannelPermissionOverrides.guildId eq guildId.toLong()) and (DiscordGuildChannelPermissionOverrides.channelId eq channel.id.value.toLong())
        }

        DiscordGuildChannels.deleteWhere {
            (DiscordGuildChannels.guildId eq guildId.toLong()) and (DiscordGuildChannels.channelId eq channel.id.value.toLong())
        }
    }

    private fun removeGuildData(guildId: Snowflake) {
        logger.info { "Removing $guildId's cached data..." }
        val guildIdAsLong = guildId.toLong()

        DiscordGuildChannels.deleteWhere {
            DiscordGuildChannels.guildId eq guildIdAsLong
        }

        DiscordGuildChannelPermissionOverrides.deleteWhere {
            DiscordGuildChannelPermissionOverrides.guildId eq guildIdAsLong
        }

        DiscordGuildRoles.deleteWhere {
            DiscordGuildRoles.guildId eq guildIdAsLong
        }

        DiscordGuildMembers.deleteWhere {
            DiscordGuildMembers.guildId eq guildIdAsLong
        }

        DiscordGuildMemberRoles.deleteWhere {
            DiscordGuildMemberRoles.guildId eq guildIdAsLong
        }

        DiscordGuilds.deleteWhere {
            DiscordGuilds.id eq guildIdAsLong
        }
    }

    private fun <T : Table, E> T.batchUpsertIfNeeded(
        data: Collection<E>,
        vararg keys: Column<*> = (primaryKey ?: throw IllegalArgumentException("primary key is missing")).columns,
        body: T.(BatchInsertStatement, E) -> Unit
    ) {
        if (data.isNotEmpty())
            batchUpsert(data, *keys, body = body)
    }
}
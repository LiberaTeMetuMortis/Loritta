package net.perfectdreams.loritta.deviousfun.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.serializer
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.pudding.utils.HashEncoder
import net.perfectdreams.loritta.deviouscache.data.*
import net.perfectdreams.loritta.deviouscache.requests.*
import net.perfectdreams.loritta.deviouscache.responses.*
import net.perfectdreams.loritta.deviousfun.DeviousFun
import net.perfectdreams.loritta.deviousfun.entities.*
import net.perfectdreams.loritta.deviousfun.events.guild.member.GuildMemberUpdateBoostTimeEvent
import net.perfectdreams.loritta.deviousfun.hooks.ListenerAdapter
import net.perfectdreams.loritta.deviousfun.utils.*
import org.jetbrains.exposed.sql.Database
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages cache
 */
class DeviousCacheManager(
    val m: DeviousFun,
    val database: Database,
    val users: SnowflakeMap<DeviousUserData>,
    val channels: SnowflakeMap<DeviousChannelData>,
    val guilds: SnowflakeMap<DeviousGuildDataWrapper>,
    val emotes: SnowflakeMap<SnowflakeMap<DeviousGuildEmojiData>>,
    val roles: SnowflakeMap<SnowflakeMap<DeviousRoleData>>,
    val members: SnowflakeMap<SnowflakeMap<DeviousMemberData>>,
    val voiceStates: SnowflakeMap<SnowflakeMap<DeviousVoiceStateData>>,
    val gatewaySessions: ConcurrentHashMap<Int, DeviousGatewaySession>
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val cacheDatabase = DeviousCacheDatabase(this, database)

    // Serialized Hashes of Entities
    // Useful to avoid acquiring a Redis connection when there wasn't any changes in the entity itself
    private val cachedUserHashes = Caffeine.newBuilder()
        .expireAfterAccess(15L, TimeUnit.MINUTES)
        .build<Snowflake, Int>()
    private val cachedMemberHashes = Caffeine.newBuilder()
        .expireAfterAccess(15L, TimeUnit.MINUTES)
        .build<Snowflake, Int>()
    // Entity specific mutexes
    val mutexes = ConcurrentHashMap<EntityKey, Mutex>()

    // A mutex, kind of
    private val entityPersistenceModificationMutex = MutableStateFlow<CacheEntityStatus>(CacheEntityStatus.OK)

    suspend fun getGuild(id: Snowflake): Guild? {
        val lightweightSnowflake = id.toLightweightSnowflake()
        withLock(GuildKey(lightweightSnowflake)) {
            logger.info { "Getting guild + entities with ID $id" }

            val cachedGuild = guilds[lightweightSnowflake] ?: return null
            val cachedGuildData = cachedGuild.data
            val roles = roles[lightweightSnowflake]?.toMap() ?: emptyMap()
            val channels = cachedGuild.channelIds.mapNotNull { channels[it] }.associateBy { it.id }
            val emojis = emotes[lightweightSnowflake]?.toMap() ?: emptyMap()

            val cacheWrapper = Guild.CacheWrapper()
            val guild = Guild(
                m,
                cachedGuildData,
                cacheWrapper
            )

            cacheWrapper.roles.putAll(
                roles.map { (id, data) ->
                    id.toKordSnowflake() to Role(
                        m,
                        guild,
                        data
                    )
                }
            )

            cacheWrapper.channels.putAll(
                channels.map { (id, data) ->
                    id.toKordSnowflake() to Channel(
                        m,
                        guild,
                        data
                    )
                }
            )

            cacheWrapper.emotes.putAll(
                emojis.map { (id, data) ->
                    id.toKordSnowflake() to DiscordGuildEmote(
                        m,
                        guild,
                        data
                    )
                }
            )

            return guild
        }
    }

    suspend fun createGuild(
        data: DiscordGuild,
        guildChannels: List<DiscordChannel>?,
    ): GuildAndJoinStatus {
        val lightweightSnowflake = data.id.toLightweightSnowflake()

        withLock(GuildKey(lightweightSnowflake)) {
            val deviousGuildData = DeviousGuildData.from(data)
            val guildMembers = data.members.value
            val guildVoiceStates = data.voiceStates.value

            val rolesData = data.roles.map { DeviousRoleData.from(it) }
            val emojisData = data.emojis.map { DeviousGuildEmojiData.from(it) }
            val channelsData = guildChannels?.map { DeviousChannelData.from(data.id, it) }
            val membersData = guildMembers?.associate { it.user.value!!.id.toLightweightSnowflake() to DeviousMemberData.from(it) }
            val voiceStatesData = guildVoiceStates?.map { DeviousVoiceStateData.from(it) }

            awaitForEntityPersistenceModificationMutex()

            // We are going to execute everything at the same time
            val cacheActions = mutableListOf<DeviousCacheDatabase.DirtyEntitiesWrapper.() -> (Unit)>()

            logger.info { "Updating guild with ID $lightweightSnowflake" }

            val cachedGuild = guilds[lightweightSnowflake]
            val isNewGuild = cachedGuild == null
            val wrapper = DeviousGuildDataWrapper(
                deviousGuildData,
                // TODO: This feels weird
                cachedGuild?.channelIds ?: channelsData?.map { it.id }?.toSet() ?: emptySet()
            )
            guilds[lightweightSnowflake] = wrapper

            cacheActions.add {
                this.guilds[lightweightSnowflake] = DatabaseCacheValue.Value(wrapper)
            }

            val currentEmotes = emotes[lightweightSnowflake]

            runIfDifferentAndNotNull(currentEmotes?.values, emojisData) {
                val newEmojis = SnowflakeMap(it.associateBy { it.id })
                emotes[lightweightSnowflake] = newEmojis

                cacheActions.add {
                    this.emojis[lightweightSnowflake] = DatabaseCacheValue.Value(newEmojis.toMap())
                }
            }

            val currentRoles = roles[lightweightSnowflake]
            runIfDifferentAndNotNull(currentRoles?.values, rolesData) {
                val newRoles = SnowflakeMap(it.associateBy { it.id })
                roles[lightweightSnowflake] = newRoles

                cacheActions.add {
                    this.roles[lightweightSnowflake] = DatabaseCacheValue.Value(newRoles.toMap())
                }
            }

            if (membersData != null) {
                val currentMembers = members[lightweightSnowflake]
                members[lightweightSnowflake] = (currentMembers ?: SnowflakeMap(members.size))
                    .also {
                        for ((id, member) in membersData) {
                            val currentMember = it[id]
                            if (currentMember != member) {
                                it[id] = member
                                cacheActions.add {
                                    this.members[GuildAndUserPair(lightweightSnowflake, id)] = DatabaseCacheValue.Value(member)
                                }
                            }
                        }
                    }
            }

            if (channelsData != null) {
                val oldChannelIds = cachedGuild?.channelIds?.toSet()

                // Remove removed channels from the global channel cache
                if (oldChannelIds != null) {
                    for (channelId in (channelsData.map { it.id } - oldChannelIds)) {
                        channels.remove(channelId)
                        cacheActions.add {
                            this.channels[channelId] = DatabaseCacheValue.Null()
                        }
                    }
                }

                // Add all channels to the guild channel cache
                val channelMappedByIds = channelsData.associateBy { it.id }

                for ((channelId, newChannel) in channelMappedByIds) {
                    val currentChannel = channels[channelId]
                    if (newChannel != currentChannel) {
                        channels[channelId] = newChannel
                        cacheActions.add {
                            this.channels[channelId] = DatabaseCacheValue.Value(newChannel)
                        }
                    }
                }
            }

            val currentVoiceStates = voiceStates[lightweightSnowflake]
            runIfDifferentAndNotNull(currentVoiceStates?.values, voiceStatesData) {
                val cachedVoiceStates = SnowflakeMap(it.associateBy { it.userId })
                voiceStates[lightweightSnowflake] = cachedVoiceStates
                cacheActions.add {
                    this.voiceStates[lightweightSnowflake] = DatabaseCacheValue.Value(cachedVoiceStates.toMap())
                }
            }

            // Trigger all cache actions in the same callback
            cacheDatabase.queue {
                cacheActions.forEach {
                    it.invoke(this)
                }
            }

            val cacheWrapper = Guild.CacheWrapper()
            val guild = Guild(
                m,
                deviousGuildData,
                cacheWrapper
            )

            if (channelsData != null) {
                for (channelData in channelsData) {
                    cacheWrapper.channels[channelData.id.toKordSnowflake()] = Channel(
                        m,
                        guild,
                        channelData
                    )
                }
            }

            for (roleData in rolesData) {
                cacheWrapper.roles[roleData.id.toKordSnowflake()] = Role(
                    m,
                    guild,
                    roleData
                )
            }

            for (emojiData in emojisData) {
                cacheWrapper.emotes[emojiData.id.toKordSnowflake()] = DiscordGuildEmote(
                    m,
                    guild,
                    emojiData
                )
            }

            return GuildAndJoinStatus(guild, isNewGuild)
        }
    }

    suspend fun deleteGuild(guildId: Snowflake) {
        val lightweightSnowflake = guildId.toLightweightSnowflake()
        withLock(GuildKey(lightweightSnowflake)) {
            awaitForEntityPersistenceModificationMutex()

            logger.info { "Deleting guild with ID $lightweightSnowflake" }

            val cachedGuild = guilds[lightweightSnowflake] ?: return
            cachedGuild.channelIds.forEach {
                channels.remove(it)
            }

            roles.remove(lightweightSnowflake)
            emotes.remove(lightweightSnowflake)
            guilds.remove(lightweightSnowflake)
            voiceStates.remove(lightweightSnowflake)

            val members = members[lightweightSnowflake]
            if (members != null)
                this.members.remove(lightweightSnowflake)


            cacheDatabase.queue {
                roles[lightweightSnowflake] = DatabaseCacheValue.Null()
                emojis[lightweightSnowflake] = DatabaseCacheValue.Null()

                members?.forEach { memberId, _ ->
                    // Bust the cache of all the members on this guild
                    this.members[GuildAndUserPair(lightweightSnowflake, memberId)] = DatabaseCacheValue.Null()
                }

                cachedGuild.channelIds.forEach {
                    // Bust the cache of all the channels on this guild
                    this.channels[it] = DatabaseCacheValue.Null()
                }

                this.voiceStates[lightweightSnowflake] = DatabaseCacheValue.Null()
            }
        }
    }

    suspend fun storeEmojis(guildId: Snowflake, emojis: List<DeviousGuildEmojiData>) {
        val lightweightSnowflake = guildId.toLightweightSnowflake()

        withLock(GuildKey(lightweightSnowflake)) {
            awaitForEntityPersistenceModificationMutex()

            logger.info { "Updating guild emojis on guild $lightweightSnowflake" }

            val newEmotes = SnowflakeMap(emojis.associateBy { it.id })
            emotes[lightweightSnowflake] = newEmotes
            val newEmotesAsMap = newEmotes.toMap()

            cacheDatabase.queue {
                this.emojis[lightweightSnowflake] = DatabaseCacheValue.Value(newEmotesAsMap)
            }
        }
    }

    suspend fun getUser(id: Snowflake): User? {
        val lightweightSnowflake = id.toLightweightSnowflake()

        withLock(UserKey(lightweightSnowflake)) {
            logger.info { "Getting user with ID $lightweightSnowflake" }
            val deviousUserData = users[lightweightSnowflake] ?: return null

            return User(
                m,
                id,
                deviousUserData
            )
        }
    }

    suspend fun createUser(user: DiscordUser, addToCache: Boolean): User {
        val deviousUserData = DeviousUserData.from(user)

        if (addToCache) {
            doIfNotMatch(cachedUserHashes, user.id, user) {
                val lightweightSnowflake = user.id.toLightweightSnowflake()
                withLock(UserKey(lightweightSnowflake)) {
                    awaitForEntityPersistenceModificationMutex()

                    logger.info { "Updating user with ID $lightweightSnowflake" }
                    users[lightweightSnowflake] = deviousUserData

                    cacheDatabase.queue {
                        this.users[lightweightSnowflake] = DatabaseCacheValue.Value(deviousUserData)
                    }
                }
            }
        }

        return User(m, user.id, deviousUserData)
    }

    suspend fun getMember(user: User, guild: Guild): Member? {
        val guildId = guild.idSnowflake.toLightweightSnowflake()
        val userId = user.idSnowflake.toLightweightSnowflake()

        withLock(GuildKey(guildId), UserKey(userId)) {
            logger.info { "Getting guild member $userId of guild $guildId" }

            val cachedMembers = members[guildId] ?: return null
            val cachedMember = cachedMembers[userId] ?: return null

            return Member(
                m,
                cachedMember,
                guild,
                user
            )
        }
    }

    suspend fun createMember(user: User, guild: Guild, member: DiscordGuildMember) =
        createMember(user, guild, DeviousMemberData.from(member))

    suspend fun createMember(user: User, guild: Guild, member: DiscordAddedGuildMember) =
        createMember(user, guild, DeviousMemberData.from(member))

    suspend fun createMember(user: User, guild: Guild, member: DiscordUpdatedGuildMember): Member {
        // Because the DiscordUpdatedGuildMember entity does not have some fields, we will use them as a copy
        val oldDeviousMemberData = getMember(user, guild)?.member
        return createMember(user, guild, DeviousMemberData.from(member, oldDeviousMemberData))
    }

    suspend fun createMember(user: User, guild: Guild, deviousMemberData: DeviousMemberData): Member {
        val member = Member(
            m,
            deviousMemberData,
            guild,
            user
        )

        val guildId = guild.idSnowflake.toLightweightSnowflake()
        val userId = user.idSnowflake.toLightweightSnowflake()

        doIfNotMatch(cachedMemberHashes, user.idSnowflake, deviousMemberData) {
            var oldMember: DeviousMemberData? = null

            withLock(GuildKey(guildId), UserKey(userId)) {
                awaitForEntityPersistenceModificationMutex()

                logger.info { "Updating guild member with ID ${userId} on guild ${guildId}" }

                val currentMembers = members[guildId]
                // Expected 1 because we will insert the new member
                members[guildId] = (currentMembers ?: SnowflakeMap(1))
                    .also {
                        oldMember = it[userId]
                        it[userId] = deviousMemberData
                    }

                cacheDatabase.queue {
                    this.members[GuildAndUserPair(guildId, userId)] = DatabaseCacheValue.Value(deviousMemberData)
                }
            }

            // Let's compare the old member x new member data to trigger events
            val oldMemberData = oldMember
            val newMemberData = deviousMemberData

            if (oldMemberData != null) {
                val oldTimeBoosted = oldMemberData.premiumSince
                val newTimeBoosted = newMemberData.premiumSince

                if (oldTimeBoosted != newTimeBoosted) {
                    m.forEachListeners(
                        GuildMemberUpdateBoostTimeEvent(
                            m,
                            // Because we don't have access to the gateway instance here, let's get the gateway manually
                            // This needs to be refactored later, because some events (example: user update) may not have a specific gateway bound to it
                            m.gatewayManager.getGatewayForGuild(guild.idSnowflake),
                            guild,
                            user,
                            member,
                            oldTimeBoosted?.toJavaInstant()?.atOffset(ZoneOffset.UTC),
                            newTimeBoosted?.toJavaInstant()?.atOffset(ZoneOffset.UTC)
                        ),
                        ListenerAdapter::onGuildMemberUpdateBoostTime
                    )
                }
            }
        }

        return member
    }

    suspend fun deleteMember(guild: Guild, userId: Snowflake) {
        val guildId = guild.idSnowflake.toLightweightSnowflake()
        val userId = userId.toLightweightSnowflake()

        withLock(GuildKey(guildId), UserKey(userId)) {
            awaitForEntityPersistenceModificationMutex()

            logger.info { "Deleting guild member with ID $userId on guild $guildId" }

            val currentMembers = members[guildId]
            members[guildId] = (currentMembers ?: SnowflakeMap(0))
                .also {
                    it.remove(userId)
                }

            cacheDatabase.queue {
                this.members[GuildAndUserPair(guildId, userId)] = DatabaseCacheValue.Null()
            }
        }
    }

    suspend fun createRole(guild: Guild, role: DiscordRole): Role {
        val data = DeviousRoleData.from(role)

        val guildId = guild.idSnowflake.toLightweightSnowflake()

        withLock(GuildKey(guildId)) {
            awaitForEntityPersistenceModificationMutex()

            logger.info { "Updating guild role with ID ${data.id} on guild $guildId" }

            val currentRoles = roles[guildId]
            // Expected 1 because we will insert the new role
            val newRoles = (currentRoles ?: SnowflakeMap(1))
                .also {
                    it[data.id] = data
                }
            val newRolesCloneAsMap = newRoles.toMap()
            roles[guildId] = newRoles

            cacheDatabase.queue {
                this.roles[guildId] = DatabaseCacheValue.Value(newRolesCloneAsMap)
            }
        }

        return Role(
            m,
            guild,
            data
        )
    }

    suspend fun deleteRole(guild: Guild, roleId: Snowflake) {
        val guildId = guild.idSnowflake.toLightweightSnowflake()
        val roleId = roleId.toLightweightSnowflake()

        // It seems that deleting a role does trigger a member update related to the role removal, so we won't need to manually remove it (yay)
        withLock(GuildKey(guildId)) {
            awaitForEntityPersistenceModificationMutex()

            logger.info { "Deleting guild role with ID ${roleId} on guild ${guildId}" }

            val currentRoles = roles[guildId]
            val newRoles = (currentRoles ?: SnowflakeMap(0))
                .also {
                    it.remove(roleId)
                }
            val newRolesCloneAsMap = newRoles.toMap()
            roles[guildId] = newRoles

            cacheDatabase.queue {
                this.roles[roleId] = DatabaseCacheValue.Value(newRolesCloneAsMap)
            }
        }
    }

    suspend fun getChannel(channelId: Snowflake): Channel? {
        val channelId = channelId.toLightweightSnowflake()
        withLock(ChannelKey(channelId)) {
            logger.info { "Getting channel $channelId" }

            val cachedChannel = channels[channelId] ?: return null
            val guildId = cachedChannel.guildId
            val cachedGuild = if (guildId != null)
                guilds[guildId]
            else
                null

            return if (cachedGuild != null && guildId != null) {
                val cachedGuildData = cachedGuild.data
                val roles = roles[guildId]?.toMap() ?: emptyMap()
                val channels = cachedGuild.channelIds.mapNotNull { channels[it] }.associateBy { it.id }
                val emojis = emotes[guildId]?.toMap() ?: emptyMap()

                val cacheWrapper = Guild.CacheWrapper()
                val guild = Guild(
                    m,
                    cachedGuildData,
                    cacheWrapper
                )

                cacheWrapper.roles.putAll(
                    roles.map { (id, data) ->
                        id.toKordSnowflake() to Role(
                            m,
                            guild,
                            data
                        )
                    }
                )

                cacheWrapper.channels.putAll(
                    channels.map { (id, data) ->
                        id.toKordSnowflake() to Channel(
                            m,
                            guild,
                            data
                        )
                    }
                )

                cacheWrapper.emotes.putAll(
                    emojis.map { (id, data) ->
                        id.toKordSnowflake() to DiscordGuildEmote(
                            m,
                            guild,
                            data
                        )
                    }
                )

                Channel(m, guild, cachedChannel)
            } else {
                Channel(m, null, cachedChannel)
            }
        }
    }

    suspend fun createChannel(guild: Guild?, data: DiscordChannel): Channel {
        val guildId = guild?.idSnowflake?.toLightweightSnowflake()
        val channelId = data.id.toLightweightSnowflake()
        val locks = mutableListOf<EntityKey>(ChannelKey(channelId))
        if (guildId != null)
            locks.add(GuildKey(guildId))

        withLock(*locks.toTypedArray()) {
            val deviousChannelData = DeviousChannelData.from(guild?.idSnowflake, data)
            val currentChannelData = channels[channelId]

            if (deviousChannelData != currentChannelData) {
                awaitForEntityPersistenceModificationMutex()

                logger.info { "Updating channel with ID ${channelId}" }
                channels[channelId] = deviousChannelData
                cacheDatabase.queue {
                    this.channels[channelId] = DatabaseCacheValue.Value(deviousChannelData)
                }

                if (guildId != null) {
                    val cachedGuild = guilds[guildId]
                    if (cachedGuild != null) {
                        // Add the channel ID to the cached guild
                        val newCachedGuild = cachedGuild.copy(channelIds = (cachedGuild.channelIds + deviousChannelData.id))
                        guilds[guildId] = newCachedGuild
                        cacheDatabase.queue {
                            this.guilds[guildId] = DatabaseCacheValue.Value(newCachedGuild)
                        }
                    } else {
                        logger.warn { "Channel $channelId requires guild $guildId, but we don't have it cached!" }
                    }
                }
            } else {
                logger.info { "Noop operation on $channelId" }
            }

            return Channel(m, guild, deviousChannelData)
        }
    }

    suspend fun deleteChannel(guild: Guild?, channelId: Snowflake) {
        val guildId = guild?.idSnowflake?.toLightweightSnowflake()
        val channelId = channelId.toLightweightSnowflake()
        val locks = mutableListOf<EntityKey>(ChannelKey(channelId))
        if (guildId != null)
            locks.add(GuildKey(guildId))

        withLock(*locks.toTypedArray()) {
            logger.info { "Deleting channel with ID ${channelId}" }

            channels.remove(channelId)

            cacheDatabase.queue {
                this.channels[channelId] = DatabaseCacheValue.Null()
            }

            if (guildId != null) {
                val cachedGuild = guilds[guildId]
                if (cachedGuild != null) {
                    // Remove the channel ID from the cached guild
                    val newCachedGuild = cachedGuild.copy(channelIds = (cachedGuild.channelIds - channelId))
                    guilds[guildId] = newCachedGuild

                    cacheDatabase.queue {
                        this.guilds[guildId] = DatabaseCacheValue.Value(newCachedGuild)
                    }
                } else {
                    logger.warn { "Channel $channelId requires guild $guildId, but we don't have it cached!" }
                }
            }
        }
    }

    /**
     * Hashes [value]'s primitives with [Objects.hash] to create a hash that identifies the object.
     */
    private inline fun <reified T> hashEntity(value: T): Int {
        // We use our own custom hash encoder because ProtoBuf can't encode the "Optional" fields, because it can't serialize null values
        // on a field that isn't marked as null
        val encoder = HashEncoder()
        encoder.encodeSerializableValue(serializer(), value)
        return Objects.hash(*encoder.list.toTypedArray())
    }

    private inline fun <reified T> doIfNotMatch(
        cache: Cache<Snowflake, Int>,
        id: Snowflake,
        data: T,
        actionIfNotMatch: () -> (Unit)
    ) {
        val hashedEntity = hashEntity(data)
        if (cache.getIfPresent(id) != hashedEntity) {
            actionIfNotMatch.invoke()
            cache.put(id, hashedEntity)
        }
    }

    /**
     * Locks the [entityKeys] for manipulation.
     *
     * The mutexes are locked on the following order:
     * * Guild
     * * Channel
     * * User
     * and the IDs are sorted per category from smallest to largest.
     *
     * This order is necessary to avoid deadlocking when two coroutines invoke withLock at the same time!
     */
    suspend inline fun <T> withLock(vararg entityKeys: EntityKey, action: () -> (T)): T {
        val sortedEntityKeys = mutableListOf<EntityKey>()

        for (entityKey in entityKeys) {
            if (entityKey is GuildKey)
                sortedEntityKeys.add(entityKey)
        }

        for (entityKey in entityKeys) {
            if (entityKey is ChannelKey)
                sortedEntityKeys.add(entityKey)
        }

        for (entityKey in entityKeys) {
            if (entityKey is UserKey)
                sortedEntityKeys.add(entityKey)
        }

        val mutexesToBeLocked = sortedEntityKeys
            .sortedBy { it.id }
            .map { mutexes.getOrPut(it) { Mutex() } }

        for (mutex in mutexesToBeLocked) {
            mutex.lock()
        }

        try {
            return action.invoke()
        } finally {
            for (mutex in mutexesToBeLocked) {
                mutex.unlock()
            }
        }
    }

    suspend fun awaitForEntityPersistenceModificationMutex() {
        // Wait until it is ok before proceeding
        entityPersistenceModificationMutex
            .filter {
                it == CacheEntityStatus.OK
            }
            .first()
    }
}
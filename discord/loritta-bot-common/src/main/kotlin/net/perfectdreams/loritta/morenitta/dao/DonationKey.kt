package net.perfectdreams.loritta.morenitta.dao

import net.perfectdreams.loritta.morenitta.tables.DonationKeys
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class DonationKey(id: EntityID<Long>) : LongEntity(id) {
	companion object : LongEntityClass<DonationKey>(DonationKeys)

	var userId by DonationKeys.userId
    var value by DonationKeys.value
    var expiresAt by DonationKeys.expiresAt
	var metadata by DonationKeys.metadata
	val activeIn by ServerConfig optionalReferencedOn DonationKeys.activeIn

    /**
     * Returns if the key is still active
     */
    fun isActive() = expiresAt >= System.currentTimeMillis()
}
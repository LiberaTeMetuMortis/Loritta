package net.perfectdreams.loritta.morenitta.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object SonhosBundles : LongIdTable() {
    val active = bool("active").index()
    val sonhos = long("sonhos")
    val price = double("price")
}
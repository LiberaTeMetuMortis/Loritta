package net.perfectdreams.loritta.morenitta.interactions.commands.options

import net.perfectdreams.i18nhelper.core.keydata.StringI18nData

open class ApplicationCommandOptions {
    companion object {
        val NO_OPTIONS = object : ApplicationCommandOptions() {}
    }

    val registeredOptions = mutableListOf<OptionReference<*>>()

    fun string(name: String, description: StringI18nData, builder: StringDiscordOptionReference<String>.() -> (Unit) = {}) = StringDiscordOptionReference<String>(name, description, true)
        .apply(builder)
        .also { registeredOptions.add(it) }

    fun optionalString(name: String, description: StringI18nData, builder: StringDiscordOptionReference<String?>.() -> (Unit) = {}) = StringDiscordOptionReference<String?>(name, description, false)
        .apply(builder)
        .also { registeredOptions.add(it) }

    fun long(name: String, description: StringI18nData, requiredRange: LongRange? = null) = LongDiscordOptionReference<Long>(name, description, true, requiredRange)
        .also { registeredOptions.add(it) }

    fun optionalLong(name: String, description: StringI18nData, requiredRange: LongRange? = null) = LongDiscordOptionReference<Long?>(name, description, false, requiredRange)
        .also { registeredOptions.add(it) }

    fun user(name: String, description: StringI18nData) = UserDiscordOptionReference<UserAndMember>(name, description, true)
        .also { registeredOptions.add(it) }

    fun optionalUser(name: String, description: StringI18nData) = UserDiscordOptionReference<UserAndMember?>(name, description, false)
        .also { registeredOptions.add(it) }
}
package net.perfectdreams.loritta.morenitta.website.views

import kotlinx.html.*
import net.dv8tion.jda.api.entities.Guild
import net.perfectdreams.i18nhelper.core.I18nContext
import net.perfectdreams.loritta.common.locale.BaseLocale
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.utils.config.EnvironmentType
import net.perfectdreams.loritta.morenitta.utils.locale.LegacyBaseLocale

abstract class GuildDashboardView(
    loritta: LorittaBot,
    i18nContext: I18nContext,
    locale: BaseLocale,
    path: String,
    private val legacyBaseLocale: LegacyBaseLocale,
    private val guild: Guild,
    private val selectedType: String,
) : NavbarView(
    loritta,
    i18nContext,
    locale,
    path
) {
    override val hasFooter = false
    override val useOldStyleCss = true

    override fun DIV.generateContent() {
        div(classes = "totallyHidden") {
            id = "locale-json"
            + LorittaBot.GSON.toJson(legacyBaseLocale.strings)
        }

        div {
            id = "server-configuration"

            nav {
                id = "left-sidebar"

                div(classes = "discord-scroller") {
                    id = "left-sidebar-contents"

                    div {
                        style = "text-align: center;"

                        img(src = guild.iconUrl) {
                            style = "border-radius: 999999px;"
                            width = "128"
                        }
                    }

                    fun appendEntry(url: String, enableLinkPreload: Boolean, name: String, icon: String, type: String) {
                        a(href = "/${locale.path}$url") {
                            if (enableLinkPreload)
                                attributes["data-enable-link-preload"] = "true"
                            if (type == "default")
                                attributes["data-general-section"] = "true"

                            div(classes = "entry") {
                                if (selectedType == type)
                                    classes = classes + "selected-entry"

                                i(classes = icon) {
                                    attributes["aria-hidden"] = "true"
                                }

                                + " "
                                + name
                            }
                        }
                    }

                    div(classes = "category") {
                        + guild.name
                    }

                    hr(classes = "divider") {}

                    appendEntry("/guild/${guild.id}/configure", true, locale["modules.sectionNames.general"], "fa fa-cogs", "default")
                    appendEntry("/guild/${guild.id}/configure/moderation", true, locale["modules.sectionNames.moderation"], "fas fa-exclamation-circle", "moderation")
                    appendEntry("/guild/${guild.id}/configure/commands", true, locale["modules.sectionNames.commands"], "fa fa-terminal", "vanilla_commands")
                    appendEntry("/guild/${guild.id}/configure/permissions", true, locale["modules.sectionNames.permissions"], "fa fa-address-card", "permissions")

                    hr(classes = "divider") {}

                    div(classes = "category") {
                        + legacyBaseLocale.strings["DASHBOARD_Notifications"]!!
                    }

                    appendEntry("/guild/${guild.id}/configure/welcomer", true, locale["modules.sectionNames.welcomer"], "fa fa-sign-in-alt", "welcomer")
                    appendEntry("/guild/${guild.id}/configure/event-log", true, locale["modules.sectionNames.eventLog"], "fa fa-eye", "event_log")
                    appendEntry("/guild/${guild.id}/configure/youtube", true, "YouTube", "fab fa-youtube", "youtube")
                    appendEntry("/guild/${guild.id}/configure/twitch", true,"Twitch", "fab fa-twitch", "livestream")
                    // appendEntry("/guild/${guild.id}/configure/twitter", true, "Twitter", "fab fa-twitter", "twitter")

                    hr(classes = "divider") {}

                    div(classes = "category") {
                        + legacyBaseLocale.strings["CommandCategory_MISC_Name"]!!
                    }

                    appendEntry("/guild/${guild.id}/configure/level", true, locale["modules.sectionNames.levelUp"], "fas fa-award", "level")
                    appendEntry("/guild/${guild.id}/configure/autorole", true, locale["modules.sectionNames.autorole"], "fa fa-briefcase", "autorole")
                    appendEntry("/guild/${guild.id}/configure/invite-blocker", true, locale["modules.sectionNames.inviteBlocker"], "fa fa-ban", "invite_blocker")
                    appendEntry("/guild/${guild.id}/configure/member-counter", true, locale["modules.sectionNames.memberCounter"], "fas fa-sort-amount-up", "member_counter")
                    appendEntry("/guild/${guild.id}/configure/starboard", true, locale["modules.sectionNames.starboard"], "fa fa-star", "starboard")
                    appendEntry("/guild/${guild.id}/configure/miscellaneous", true, "+${legacyBaseLocale.strings["CommandCategory_MISC_Name"]}", "fas fa-random", "miscellaneous")
                    appendEntry("/guild/${guild.id}/configure/audit-log", true, locale["modules.auditLog.title"], "fas fa-list", "audit_log")

                    hr(classes = "divider") {}

                    if (loritta.config.loritta.environment == EnvironmentType.CANARY) {
                        div(classes = "category") {
                            +"GamerSafer"
                        }

                        appendEntry(
                            "/guild/${guild.id}/configure/gamersafer-verify",
                            true,
                            "Verificação de Contas",
                            "fas fa-list",
                            "gamersafer_verify"
                        )
                    }
                    
                    hr(classes = "divider") {}

                    div(classes = "category") {
                        + "Premium"
                    }

                    appendEntry("/guild/${guild.id}/configure/premium", true, locale["modules.sectionNames.premiumKeys"], "fas fa-gift", "premium")

                    appendEntry("/guild/${guild.id}/configure/badge", true, locale["modules.sectionNames.customBadge"], "fas fa-certificate", "badge")

                    appendEntry("/guild/${guild.id}/configure/daily-multiplier", true, locale["modules.sectionNames.dailyMultiplier"], "fas fa-times", "daily_multiplier")

                    hr(classes = "divider") {}

                    div(classes = "category") {
                        + legacyBaseLocale.strings["DASHBOARD_YourFeatures"]!!
                    }
                    appendEntry("/guild/${guild.id}/configure/custom-commands", true, locale["modules.sectionNames.customCommands"], "fas fa-code", "custom_commands")

                    hr(classes = "divider") {}

                    a(href = "/support") {
                        div(classes = "entry") {
                            i(classes = "fas fa-question-circle") {
                                attributes["aria-hidden"] = "true"
                            }

                            + locale["website.navbar.support"]
                        }
                    }
                }
            }

            div {
                id = "right-sidebar"

                div {
                    id = "right-sidebar-contents"

                    generateRightSidebarContents()
                }
            }

            aside {
                id = "that-wasnt-very-cash-money-of-you"

                ins(classes = "adsbygoogle") {
                    style = "display:block; position: absolute; width: inherit; max-width: 100%;"
                    attributes["data-ad-client"] = "ca-pub-9989170954243288"
                    attributes["data-ad-slot"] = "3177212938"
                    attributes["data-ad-format"] = "auto"
                    attributes["data-full-width-responsive"] = "true"
                }
            }
        }
    }

    abstract fun DIV.generateRightSidebarContents()
}
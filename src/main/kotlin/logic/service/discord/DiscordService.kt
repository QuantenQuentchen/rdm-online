package org.rdm.online.rdm.discord

import org.rdm.online.api.internal.exceptions.InternalServerErrorException
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import dev.kord.rest.request.RestRequestException
import dev.kord.rest.service.RestClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.rdm.online.api.net.rest.data.UserReply
import io.ktor.client.plugins.expectSuccess
import java.time.Duration
import org.rdm.online.Env

/** The slice of a guild member we care about. This is what gets cached. */
data class MemberInfo(
    val username: String?,
    val nick: String?,
    val guildAvatar: String?,
    val userAvatar: String?,
    val roles: Set<String>,
) {
    val isVoter get() = Env.memberRoleId in roles
    val isAdmin get() = Env.adminRoleId in roles || Env.viceAdminRoleId in roles
}

@Serializable
private class OAuthUser(val id: String)

/**
 * Discord access. Bot calls go through kord-rest (REST only, no gateway connection).
 *
 * Display data (name/avatar/roles shown to users) is cached for [DISPLAY_TTL].
 * Anything that *authorizes* (login eligibility, admin checks) bypasses the cache.
 */
class DiscordService(private val http: HttpClient) {
    private val log = LoggerFactory.getLogger(DiscordService::class.java)
    private val rest = RestClient(Env.discordBotToken)
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(DISPLAY_TTL)
        .maximumSize(10_000)
        .build<String, MemberInfo>()

    /** null = not a member of the guild. Throws on Discord/network errors. */
    suspend fun member(userId: String, fresh: Boolean = false): MemberInfo? {
        if (!fresh) cache.getIfPresent(userId)?.let { return it }
        val found = fetch(userId)
        if (found == null) cache.invalidate(userId) else cache.put(userId, found)
        return found
    }

    private suspend fun fetch(userId: String): MemberInfo? = try {
        val m = rest.guild.getGuildMember(Snowflake(Env.guildID), Snowflake(userId))
        MemberInfo(
            username = m.user.value?.username,
            nick = m.nick.value,
            guildAvatar = m.avatar.value,
            userAvatar = m.user.value?.avatar,
            roles = m.roles.mapTo(HashSet()) { it.toString() },
        )
    } catch (e: RestRequestException) {
        if (e.status.code == 404) null else throw e
    }

    private suspend fun memberOrNull(userId: String, fresh: Boolean = false): MemberInfo? = try {
        member(userId, fresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("discord member lookup failed for {}: {}", userId, e.message)
        null
    }

    /** Fresh lookup for the login flow. Same error messages as the Go version. */
    suspend fun memberForLogin(userId: String): MemberInfo {
        val m = try {
            member(userId, fresh = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InternalServerErrorException("failed to get guild member: ${e.message}")
        }
        return m ?: throw InternalServerErrorException("failed to get guild member: user is not a member of the guild")
    }

    /** Any failure counts as "not an admin". Always fresh. */
    suspend fun isAdmin(userId: String): Boolean = memberOrNull(userId, fresh = true)?.isAdmin == true

    /** Exchanges an OAuth access token for the Discord user id (`identify` scope). */
    suspend fun oauthUserId(accessToken: String): String = try {
        http.get("$API/users/@me") {
            expectSuccess = true
            bearerAuth(accessToken)
        }.body<OAuthUser>().id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("failed to get discord user: {}", e.message)
        throw InternalServerErrorException("failed to get discord user")
    }

    suspend fun userReply(discordId: String): UserReply {
        val m = memberOrNull(discordId)
            ?: throw InternalServerErrorException("failed to generate user reply")

        val username = m.username ?: "Discord did a fucky-wucky"
        // NOTE: kept from the Go version. Guild avatar hashes actually live under
        // /guilds/{gid}/users/{uid}/avatars/{hash}, so this URL is wrong for those.
        val avatar = m.guildAvatar ?: m.userAvatar

        return UserReply(
            id = discordId,
            displayName = m.nick?.takeIf { it.isNotEmpty() } ?: username,
            username = username,
            avatarUrl = avatar?.let { "https://cdn.discordapp.com/avatars/$discordId/$it" },
            roles = buildList {
                if (Env.memberRoleId in m.roles) add("voter")
                if (Env.viceAdminRoleId in m.roles) add("admin")
                if (Env.adminRoleId in m.roles) add("admin")
            },
        )
    }

    private companion object {
        const val API = "https://discord.com/api/v10"
        val DISPLAY_TTL: Duration = Duration.ofMinutes(5)
    }
}

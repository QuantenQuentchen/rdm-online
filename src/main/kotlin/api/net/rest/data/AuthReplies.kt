package org.rdm.online.api.net.rest.data

import kotlinx.serialization.Serializable

@Serializable
data class UserReply(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val roles: List<String>,
)

@Serializable
data class SessionReply(val user: UserReply)

@Serializable
data class IsAdminReply(val isAdmin: Boolean)

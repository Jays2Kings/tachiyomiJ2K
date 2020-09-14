package eu.kanade.tachiyomi.data.track.bangumi

data class Collection(
    val `private`: Int? = 0,
    val comment: String? = "",
    val ep_status: Int? = 0,
    val lasttouch: Int? = 0,
    val rating: Int? = 0,
    val status: Status? = Status(),
    val tag: List<String?>? = listOf(),
    val user: User? = User(),
    val vol_status: Int? = 0
)

data class OAuth(
    val access_token: String,
    val token_type: String,
    val created_at: Long,
    val expires_in: Long,
    val refresh_token: String?,
    val user_id: Long?
) {
    // Access token refresh before expired
    fun isExpired() = (System.currentTimeMillis() / 1000) > (created_at + expires_in - 3600)
}

data class Status(
    val id: Int? = 0,
    val name: String? = "",
    val type: String? = ""
)

data class User(
    val avatar: Avatar? = Avatar(),
    val id: Int? = 0,
    val nickname: String? = "",
    val sign: String? = "",
    val url: String? = "",
    val usergroup: Int? = 0,
    val username: String? = ""
)

data class Avatar(
    val large: String? = "",
    val medium: String? = "",
    val small: String? = ""
)

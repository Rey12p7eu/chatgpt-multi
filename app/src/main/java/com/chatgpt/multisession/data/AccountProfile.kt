
package com.chatgpt.multisession.data

import java.util.UUID

data class AccountProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val profileName: String = "acc_" + id.replace("-", "").take(12),
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun createNew(displayName: String, order: Int): AccountProfile {
            val id = UUID.randomUUID().toString()
            return AccountProfile(
                id = id,
                displayName = displayName,
                profileName = "acc_" + id.replace("-", "").take(16),
                order = order
            )
        }
    }
}

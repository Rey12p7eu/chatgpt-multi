
package com.chatgpt.multisession.data

import com.chatgpt.multisession.storage.AccountStorage
import kotlinx.coroutines.flow.first

class AccountRepository(private val storage: AccountStorage) {
    val accountsFlow = storage.accountsFlow
    val activeIdFlow = storage.activeAccountIdFlow

    suspend fun addAccount(displayName: String): AccountProfile {
        val current = storage.accountsFlow.first()
        val newAcc = AccountProfile.createNew(displayName, current.size)
        val updated = current + newAcc
        storage.saveAccounts(updated)
        if (current.isEmpty()) {
            storage.setActiveAccountId(newAcc.id)
        }
        return newAcc
    }

    suspend fun rename(id: String, newName: String) {
        val current = storage.accountsFlow.first()
        val updated = current.map { if (it.id == id) it.copy(displayName = newName) else it }
        storage.saveAccounts(updated)
    }

    suspend fun delete(id: String) {
        val current = storage.accountsFlow.first()
        val updated = current.filterNot { it.id == id }
        storage.saveAccounts(updated)
    }

    suspend fun reorder(fromIndex: Int, toIndex: Int) {
        val current = storage.accountsFlow.first().toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            val reordered = current.mapIndexed { idx, acc -> acc.copy(order = idx) }
            storage.saveAccounts(reordered)
        }
    }

    suspend fun setActive(id: String) {
        storage.setActiveAccountId(id)
        val current = storage.accountsFlow.first()
        val updated = current.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        storage.saveAccounts(updated)
    }

    suspend fun updateLastUsed(id: String) {
        val current = storage.accountsFlow.first()
        val updated = current.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        storage.saveAccounts(updated)
    }
}

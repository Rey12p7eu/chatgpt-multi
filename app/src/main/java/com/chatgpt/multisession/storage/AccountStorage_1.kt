
package com.chatgpt.multisession.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatgpt.multisession.data.AccountProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "accounts_prefs")
private val gson = Gson()

class AccountStorage(private val context: Context) {
    private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")
    private val ACTIVE_KEY = stringPreferencesKey("active_account_id")

    val accountsFlow: Flow<List<AccountProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[ACCOUNTS_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<AccountProfile>>() {}.type
            val list: List<AccountProfile> = gson.fromJson(json, type) ?: emptyList()
            list.sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val activeAccountIdFlow: Flow<String?> = context.dataStore.data.map { it[ACTIVE_KEY] }

    suspend fun saveAccounts(accounts: List<AccountProfile>) {
        val sorted = accounts.sortedBy { it.order }.mapIndexed { idx, acc -> acc.copy(order = idx) }
        val json = gson.toJson(sorted)
        context.dataStore.edit { it[ACCOUNTS_KEY] = json }
    }

    suspend fun setActiveAccountId(id: String) {
        context.dataStore.edit { it[ACTIVE_KEY] = id }
    }

    suspend fun getAccountsOnce(): List<AccountProfile> {
        val prefs = context.dataStore.data.map { it[ACCOUNTS_KEY] ?: "[]" }
        // quick blocking read via first - caller should use flow first()
        return emptyList()
    }
}

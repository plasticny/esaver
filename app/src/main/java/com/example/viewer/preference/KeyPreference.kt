package com.example.viewer.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val DB_NAME = "keyPreference"
private val Context.keyPreference: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class KeyPreference (context: Context): BasePreference() {
    companion object {
        @Volatile
        private var instance: KeyPreference? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: KeyPreference(context).also { instance = it }
        }
    }

    override val dataStore = context.keyPreference

    private val storeKeys = object {
        fun ruUserId () = stringPreferencesKey("ruUserId")
        fun ruApiKey () = stringPreferencesKey("ruApiKey")
    }

    fun isRuReady (): Boolean = (getRuUserId() != "") && (getRuApiKey() != "")

    fun getRuUserId (): String = read(storeKeys.ruUserId()) ?: ""

    fun getRuApiKey (): String = read(storeKeys.ruApiKey()) ?: ""

    fun setRuUserId (userId: String) = store(storeKeys.ruUserId(), userId)

    fun setRuApiKey (apiKey: String) = store(storeKeys.ruApiKey(), apiKey)
}
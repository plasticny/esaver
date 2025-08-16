package com.example.viewer.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

abstract class BasePreference protected constructor () {
    protected class CustomPreferencesKey<T> (name: String) {
        val key = byteArrayPreferencesKey(name)
    }

    protected abstract val dataStore: DataStore<Preferences>

    protected fun<T> isKeyExist (key: Preferences.Key<T>) = runBlocking {
        dataStore.data.map { it.contains(key) }.first()
    }
    protected fun<T> isKeyExist (key: CustomPreferencesKey<T>) = isKeyExist(key.key)

    protected fun<T> store (key: Preferences.Key<T>, v: T) {
        runBlocking { dataStore.edit { it[key] = v } }
    }
    protected fun<T> store (key: CustomPreferencesKey<T>, v: T) {
        val outputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).writeObject(v)
        store(key.key, outputStream.toByteArray())
    }

    protected fun<T> read (key: Preferences.Key<T>): T? {
        var res: T? = null
        runBlocking {
            res = dataStore.data.map { it[key] }.first()
        }
        return res
    }
    protected inline fun<reified T> read (key: CustomPreferencesKey<T>): T? {
        val data = read(key.key) ?: return null
        return ObjectInputStream(ByteArrayInputStream(data)).readObject() as T
    }

    protected fun<T> remove (key: Preferences.Key<T>) {
        runBlocking { dataStore.edit { it.remove(key) } }
    }
    protected fun<T> remove (key: CustomPreferencesKey<T>) = remove(key.key)
}
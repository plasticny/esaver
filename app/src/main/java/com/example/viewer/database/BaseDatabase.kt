package com.example.viewer.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

abstract class BaseDatabase protected constructor () {
    protected abstract val dataStore: DataStore<Preferences>

    protected fun<T> isKeyExist (key: Preferences.Key<T>) = runBlocking {
        dataStore.data.map { it.contains(key) }.first()
    }

    protected fun<T> store (key: Preferences.Key<T>, v: T) {
        runBlocking { dataStore.edit { it[key] = v } }
    }
    protected fun<T> storeAsByteArray (key: Preferences.Key<ByteArray>, v: T) {
        val outputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).writeObject(v)
        store(key, outputStream.toByteArray())
    }

    protected fun<T> read (key: Preferences.Key<T>): T? {
        var res: T? = null
        runBlocking {
            res = dataStore.data.map { it[key] }.first()
        }
        return res
    }
    protected inline fun<reified T> readFromByteArray (key: Preferences.Key<ByteArray>): T? {
        val data = read(key) ?: return null
        return ObjectInputStream(ByteArrayInputStream(data)).readObject() as T
    }

    protected fun<T> remove (key: Preferences.Key<T>) {
        runBlocking { dataStore.edit { it.remove(key) } }
    }
}
package com.example.viewer.database

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.vector.Group
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

abstract class BaseDatabase protected constructor () {
    companion object {
        private const val DB_EXT = "preferences_pb"
        private val HEADER_END_TAG_BYTES = "29448d15-9254-45e8-87b9-835a1cc7cf0c".toByteArray()

        fun backupDb (context: Context, backupFileUri: Uri) {
            val bookBytes = readDbBytes(context, BookDatabase.NAME)
            val groupBytes = readDbBytes(context, GroupDatabase.NAME)
            val searchBytes = readDbBytes(context, SearchDatabase.NAME)
            val backupBytes = mutableListOf<Byte>().apply {
                addAll("${bookBytes.size} ${groupBytes.size} ${searchBytes.size}".toByteArray().toList())
                addAll(HEADER_END_TAG_BYTES.toList())
                addAll(bookBytes)
                addAll(groupBytes)
                addAll(searchBytes)
            }

            context.contentResolver.openFileDescriptor(backupFileUri, "w")?.use { fd ->
                FileOutputStream(fd.fileDescriptor).use { fos ->
                    fos.write(backupBytes.toByteArray())
                }
            }
        }

        fun importDb (context: Context, backupFileUri: Uri) {
            val dbFolder = File("${context.filesDir}/datastore")

            val bytes = context.contentResolver.openFileDescriptor(backupFileUri, "r")!!.use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    fis.readAllBytes()
                }
            }

            val tagStart = findHeaderTagIndex(bytes)
            val tagEnd = tagStart + HEADER_END_TAG_BYTES.size - 1
            val sizes = String(bytes.slice(0 until tagStart).toByteArray()).split(' ').map { it.toInt() }

            val bookStart = tagEnd + 1
            val groupStart = bookStart + sizes[0]
            val searchStart = groupStart + sizes[1]

            val bookBytes = bytes.slice(bookStart until groupStart).toByteArray()
            val groupBytes = bytes.slice(groupStart until searchStart).toByteArray()
            val searchBytes = bytes.slice(searchStart .. bytes.lastIndex).toByteArray()

            FileOutputStream(File(dbFolder, "${BookDatabase.NAME}.$DB_EXT")).use { it.write(bookBytes) }
            FileOutputStream(File(dbFolder, "${GroupDatabase.NAME}.$DB_EXT")).use { it.write(groupBytes) }
            FileOutputStream(File(dbFolder, "${SearchDatabase.NAME}.$DB_EXT")).use { it.write(searchBytes) }
        }

        private fun readDbBytes (context: Context, name: String) = try {
            FileInputStream(File("${context.filesDir}/datastore", "$name.$DB_EXT")).use {
                it.readAllBytes().toList()
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            listOf()
        }

        private fun findHeaderTagIndex (bytes: ByteArray): Int {
            for (i in 0 .. bytes.size - HEADER_END_TAG_BYTES.size) {
                if ((i until i + HEADER_END_TAG_BYTES.size).all { bytes[it] == HEADER_END_TAG_BYTES[it - i] }) {
                    return i
                }
            }
            throw Exception("[${this::class.simpleName}.${this::findHeaderTagIndex.name}] header tag not found")
        }
    }

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
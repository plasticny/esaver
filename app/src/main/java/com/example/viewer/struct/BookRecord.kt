package com.example.viewer.struct

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.example.viewer.Util
import com.example.viewer.database.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class BookRecord (
    val id: String,
    val url: String,
    val coverUrl: String,
    val cat: String,
    val title: String,
    val subtitle: String = "",
    val pageNum: Int,
    val tags: Map<String, List<String>>,
    val groupId: Int = -1,
    val uploader: String? = null
): Parcelable {
    companion object CREATOR : Parcelable.Creator<BookRecord> {
        override fun createFromParcel(parcel: Parcel): BookRecord {
            return BookRecord(parcel)
        }

        override fun newArray(size: Int): Array<BookRecord?> {
            return arrayOfNulls(size)
        }
     }

    private constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readBundle(ClassLoader.getSystemClassLoader())!!.let { bundle ->
            bundle.keySet().associateWith { bundle.getStringArray(it)!!.toList() }
        },
        parcel.readInt(),
        parcel.readString()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(url)
        parcel.writeString(coverUrl)
        parcel.writeString(cat)
        parcel.writeString(title)
        parcel.writeString(subtitle)
        parcel.writeInt(pageNum)
        parcel.writeBundle(
            Bundle().apply {
                for ((key, value) in tags) {
                    putStringArray(key, value.toTypedArray())
                }
            }
        )
        parcel.writeInt(groupId)
        parcel.writeString(uploader)
    }
}

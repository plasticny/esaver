package com.example.viewer.struct

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

data class BookRecordT (
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
    companion object CREATOR : Parcelable.Creator<BookRecordT> {
        override fun createFromParcel(parcel: Parcel): BookRecordT {
            return BookRecordT(parcel)
        }

        override fun newArray(size: Int): Array<BookRecordT?> {
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

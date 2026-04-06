package com.example.viewer.activity.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NavViewModel: ViewModel() {
    private val mutableDoOpenMenu = MutableLiveData<Boolean>()
    val doOpenMenu: LiveData<Boolean> get() = mutableDoOpenMenu

    fun setOpenStatus (value: Boolean) {
        mutableDoOpenMenu.value = value
    }
}
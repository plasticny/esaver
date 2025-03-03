package com.example.viewer.activity.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.viewer.History
import com.example.viewer.R
import com.example.viewer.databinding.MainActivityBinding

class MainActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        History.init(baseContext)

        MainActivityBinding.inflate(layoutInflater).let {
            setContentView(it.root)
            it.mainActivityNavView.setupWithNavController(
                findNavController(R.id.main_activity_nav_host)
            )
        }
    }
}
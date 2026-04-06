package com.example.viewer.activity.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.viewer.R
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.databinding.MainActivityBinding

class MainActivity: AppCompatActivity() {
    private val navViewModel: NavViewModel by viewModels()
    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize exclude tag repo
        ExcludeTagRepository(baseContext)

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mainActivityNavView.setupWithNavController(
            findNavController(R.id.main_activity_nav_host)
        )

        navViewModel.doOpenMenu.observe(this, Observer { value ->
            if (value) {
                binding.root.openDrawer(GravityCompat.END)
                navViewModel.setOpenStatus(false)
            }
        })
    }
}
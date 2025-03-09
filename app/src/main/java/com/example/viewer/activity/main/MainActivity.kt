package com.example.viewer.activity.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.viewer.dataset.BookDataset
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.databinding.MainActivityBinding

class MainActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BookDataset.init(baseContext)

        MainActivityBinding.inflate(layoutInflater).let { binding ->
            setContentView(binding.root)

//            binding.mainActivityNavView.apply {
//                setNavigationItemSelectedListener { item ->
//                    val selectedFragment = when (item.itemId) {
//                        R.id.main_nav_gallery -> GalleryFragment()
//                        R.id.main_nav_search -> SearchFragment()
//                        else -> throw Exception("unexpected item selected")
//                    }
//
//                    supportFragmentManager.beginTransaction()
//                        .replace(R.id.main_activity_nav_host, selectedFragment)
//                        .commit()
//                    binding.root.closeDrawers()
//                    true
//                }
//            }

            binding.mainActivityNavView.setupWithNavController(
                findNavController(R.id.main_activity_nav_host)
            )
        }
    }
}
package com.example.viewer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class PassActivity: AppCompatActivity() {
    companion object {
        private const val PASS = "12540"
    }

    private val entered = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pass)

        Dev.run(this)

        for (i in 0..9) {
            findViewById<ImageView>(resources.getIdentifier("pass_$i", "id", packageName)).setOnClickListener {
                entered.add(i)
                if (i == 0) {
                    check()
                }
            }
        }
        for (i in 1..2) {
            findViewById<ImageView>(resources.getIdentifier("pass_dummy$i", "id", packageName)).setOnClickListener {
                entered.add(10)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        entered.clear()
    }

    private fun check () {
        if (entered.size != 5 || entered.joinToString("") != PASS) {
            return
        }
        val intent = Intent(this, GalleryActivity::class.java)
        this.startActivity(intent)
    }
}
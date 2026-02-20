package com.example.viewer.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.viewer.Dev
import com.example.viewer.activity.main.MainActivity
import com.example.viewer.databinding.PassActivityBinding
import kotlinx.coroutines.launch

class PassActivity: AppCompatActivity() {
    companion object {
        private const val PASS = "12540"
    }

    private val entered = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = PassActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            Dev().run(baseContext)
        }

        val passButtons = listOf(
            binding.pass0, binding.pass1, binding.pass2, binding.pass3,
            binding.pass4, binding.pass5, binding.pass6, binding.pass7,
            binding.pass8, binding.pass9
        )
        for ((idx, pass) in passButtons.withIndex()) {
            pass.setOnClickListener {
                entered.add(idx)
                if (idx == 0) {
                    check()
                }
            }
        }
        for (pass in listOf(binding.passDummy1, binding.passDummy2)) {
            pass.setOnClickListener {
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
        val intent = Intent(this, MainActivity::class.java)
        this.startActivity(intent)
    }
}
package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MotivationalQuoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        val tvQuoteText: TextView = findViewById(R.id.tvQuoteText)
        val btnCloseQuote: Button = findViewById(R.id.btnCloseQuote)

        val quotes = resources.getStringArray(R.array.motivational_quotes)
        if (quotes.isNotEmpty()) {
            val randomIndex = Random.nextInt(quotes.size)
            tvQuoteText.text = quotes[randomIndex]
        }

        btnCloseQuote.setOnClickListener {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Disable back button to force user to use the "I'm done scrolling" button
        // or just let it work? Usually better to allow it, but for "blocking" it might be better to disable.
        // Let's keep it simple and allow it for now, or just finish().
        super.onBackPressed()
    }
}

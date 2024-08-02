package com.learn.translatorapp

import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.learn.translatorapp.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var dbHelper: TranslationDBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = TranslationDBHelper(this)
        displayHistory()
    }

    private fun displayHistory() {
        val historyList = dbHelper.getAllTranslations()
        val adapter = HistoryAdapter(this, historyList)
        binding.historyListView.adapter = adapter
    }
}

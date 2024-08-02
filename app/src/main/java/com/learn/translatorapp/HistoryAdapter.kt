package com.learn.translatorapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class HistoryAdapter(context: Context, private val historyList: List<TranslationHistory>)
    : ArrayAdapter<TranslationHistory>(context, 0, historyList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.activity_history_adapter, parent, false)
        val history = getItem(position) ?: return view

        val sourceTextView = view.findViewById<TextView>(R.id.sourceTextView)
        val translatedTextView = view.findViewById<TextView>(R.id.translatedTextView)

        sourceTextView.text = history.sourceText
        translatedTextView.text = history.translatedText

        return view
    }
}

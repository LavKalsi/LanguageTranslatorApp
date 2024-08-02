package com.learn.translatorapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TranslationDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "translations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "translations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SOURCE_TEXT = "source_text"
        private const val COLUMN_TRANSLATED_TEXT = "translated_text"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE $TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SOURCE_TEXT TEXT, $COLUMN_TRANSLATED_TEXT TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertTranslation(sourceText: String, translatedText: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SOURCE_TEXT, sourceText)
            put(COLUMN_TRANSLATED_TEXT, translatedText)
        }
        val result = db.insert(TABLE_NAME, null, values)
        db.close()
        return result != -1L
    }

    fun getAllTranslations(): List<TranslationHistory> {
        val translations = mutableListOf<TranslationHistory>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)
        with(cursor) {
            while (moveToNext()) {
                val sourceText = getString(getColumnIndexOrThrow(COLUMN_SOURCE_TEXT))
                val translatedText = getString(getColumnIndexOrThrow(COLUMN_TRANSLATED_TEXT))
                translations.add(TranslationHistory(sourceText, translatedText))
            }
            close()
        }
        db.close()
        return translations
    }
}

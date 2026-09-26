package com.baastiklabs.firewatch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.baastiklabs.firewatch.core.records.RecordEnvelope

/**
 * The phone's database: one table of JSON records whose schema never changes (version 1
 * forever). That is what lets any version, including a rollback, open data written by any
 * other version. New features add record types or JSON fields, never columns.
 */
class RecordStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "type TEXT NOT NULL, " +
                "ts INTEGER, " +
                "updated_at INTEGER NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0, " +
                "json TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX records_type_ts ON records(type, ts)")
    }

    // The schema is frozen at version 1, so there is never anything to migrate.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // Never throw or drop data on a downgrade.
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<RecordEnvelope> {
        val result = ArrayList<RecordEnvelope>()
        readableDatabase.query("records", COLUMNS, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                result += RecordEnvelope(
                    id = c.getString(0),
                    type = c.getString(1),
                    ts = if (c.isNull(2)) null else c.getLong(2),
                    updatedAt = c.getLong(3),
                    deleted = c.getInt(4) != 0,
                    json = c.getString(5),
                )
            }
        }
        return result
    }

    fun putAll(records: Collection<RecordEnvelope>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in records) {
                val values = ContentValues().apply {
                    put("id", r.id)
                    put("type", r.type)
                    if (r.ts == null) putNull("ts") else put("ts", r.ts)
                    put("updated_at", r.updatedAt)
                    put("deleted", if (r.deleted) 1 else 0)
                    put("json", r.json)
                }
                db.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DB_NAME = "firewatch.db"
        private val COLUMNS = arrayOf("id", "type", "ts", "updated_at", "deleted", "json")
    }
}

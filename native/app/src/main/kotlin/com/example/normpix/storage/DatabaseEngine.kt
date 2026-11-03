package com.example.normpix.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * How the app's databases (the finance and reminders ones) are opened: [open] gives the helper a database is read and written
 * through, for the file [name] in the app's databases folder - or, with a null [name], a private one in memory - set up by
 * [callback] (its schema, its migrations).
 */
fun interface DatabaseEngine {
    fun open(context: Context, name: String?, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper

    companion object {
        /**
         * Plain SQLite, as the app always kept its data: what the tests use, what the sample data uses (in memory), and what a
         * phone uses where the encrypted database can't be (see [EncryptedDatabases]).
         */
        val Plain = DatabaseEngine { context, name, callback ->
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
            )
        }
    }
}

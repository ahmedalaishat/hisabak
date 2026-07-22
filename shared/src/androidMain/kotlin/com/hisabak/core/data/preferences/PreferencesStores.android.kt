package com.hisabak.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

/** Same on-disk location the `preferencesDataStore` delegate used, so existing
 *  installs keep their preferences across the KMP migration. */
fun preferencesDataStore(context: Context, name: String): DataStore<Preferences> =
    createPreferencesDataStore {
        File(context.applicationContext.filesDir, "datastore/$name.preferences_pb")
            .absolutePath.toPath()
    }

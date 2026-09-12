package com.legtracking

import android.app.Application
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration

class LegTrackingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // osmdroid butuh user agent unik & folder cache sendiri, kalau tidak
        // permintaan tile ke server OSM bisa ditolak.
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName
    }
}

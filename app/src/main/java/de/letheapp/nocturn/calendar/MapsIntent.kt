package de.letheapp.nocturn.calendar

import android.content.Intent
import android.net.Uri

/**
 * Baut einen `geo:0,0?q=<Adresse>`-Intent für die installierte Karten-App (Google Maps, OsmAnd,
 * Organic Maps, ...). Mehrzeilige Adressen werden zu einer kommagetrennten Zeile zusammengefasst.
 * Gemeinsam genutzt von der Tagesansicht und der Termin-Erinnerungs-Benachrichtigung.
 */
fun buildMapsIntent(location: String): Intent {
    val query = location.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

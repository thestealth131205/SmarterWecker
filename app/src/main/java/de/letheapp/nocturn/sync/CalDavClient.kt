package de.letheapp.nocturn.sync

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class CalDavException(message: String) : Exception(message)

data class CalDavCalendarInfo(val href: String, val displayName: String)

data class CalDavRemoteEvent(val href: String, val etag: String, val icsData: String)

private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
private val CALENDAR_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()

/**
 * Minimaler WebDAV/CalDAV-Client für die Nextcloud-Synchronisierung (siehe CLAUDE.md
 * "Synchronisierbar mit ... Nextcloud (CalDAV)"). Bewusst ohne dav4jvm o.ä. implementiert (FOSS-
 * Leitlinie: möglichst wenige Zusatz-Abhängigkeiten, nur OkHttp + Androids eingebauter
 * XmlPullParser), da nur eine kleine, klar begrenzte Teilmenge von WebDAV benötigt wird:
 * Kalender-Discovery (PROPFIND) und Event-Abgleich (REPORT/PUT/DELETE).
 */
@Singleton
class CalDavClient @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Ermittelt per CalDAV-Discovery (current-user-principal -> calendar-home-set) alle Kalender des Kontos. */
    suspend fun discoverCalendars(serverUrl: String, username: String, password: String): List<CalDavCalendarInfo> {
        val base = resolveDavBase(serverUrl, username, password)

        val principalHref = propfindOrNull(base, username, password, PRINCIPAL_BODY)
            ?.findDescendant("current-user-principal")?.findDescendant("href")?.text
        val principalUrl = principalHref?.let { base.resolve(it) } ?: base

        val homeSetHref = propfindOrNull(principalUrl, username, password, HOME_SET_BODY)
            ?.findDescendant("calendar-home-set")?.findDescendant("href")?.text
        val homeSetUrl = homeSetHref?.let { base.resolve(it) } ?: principalUrl

        val root = propfind(homeSetUrl, username, password, depth = 1, body = LIST_CALENDARS_BODY)
        return root.allChildren("response").mapNotNull { response ->
            val href = response.findDescendant("href")?.text ?: return@mapNotNull null
            val prop = successfulProp(response) ?: return@mapNotNull null
            val isCalendar = prop.firstChild("resourcetype")?.children?.any { it.name == "calendar" } == true
            if (!isCalendar) return@mapNotNull null
            val displayName = prop.firstChild("displayname")?.text.orEmpty()
            val absoluteHref = homeSetUrl.resolve(href)?.toString() ?: href
            CalDavCalendarInfo(
                href = absoluteHref,
                displayName = displayName.ifBlank { href.trimEnd('/').substringAfterLast('/') }
            )
        }
    }

    /** Alle VEVENTs eines Kalenders inkl. ETag, per REPORT calendar-query in einem Durchgang. */
    suspend fun fetchEvents(calendarHref: String, username: String, password: String): List<CalDavRemoteEvent> {
        val url = calendarHref.toHttpUrlOrNull() ?: throw CalDavException("Ungültige Kalender-Adresse: $calendarHref")
        val root = request(url, "REPORT", username, password, depth = 1, body = EVENT_QUERY_BODY)
        return root.allChildren("response").mapNotNull { response ->
            val href = response.findDescendant("href")?.text ?: return@mapNotNull null
            val prop = successfulProp(response) ?: return@mapNotNull null
            val etag = prop.firstChild("getetag")?.text ?: return@mapNotNull null
            val icsData = prop.firstChild("calendar-data")?.text ?: return@mapNotNull null
            CalDavRemoteEvent(href = url.resolve(href)?.toString() ?: href, etag = etag, icsData = icsData)
        }
    }

    /** Legt ein Event an oder ersetzt es (PUT). [ifMatchEtag] = null bei Neuanlage. Liefert die neue ETag, falls vom Server mitgeschickt. */
    suspend fun putEvent(href: String, username: String, password: String, icsData: String, ifMatchEtag: String?): String? {
        val url = href.toHttpUrlOrNull() ?: throw CalDavException("Ungültige Event-Adresse: $href")
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .put(icsData.toRequestBody(CALENDAR_MEDIA_TYPE))
        if (ifMatchEtag != null) builder.header("If-Match", ifMatchEtag) else builder.header("If-None-Match", "*")

        return executeHttp(builder.build()) { response ->
            if (!response.isSuccessful) throw CalDavException("Nextcloud-Fehler beim Speichern (${response.code})")
            response.header("ETag")
        }
    }

    suspend fun deleteEvent(href: String, username: String, password: String) {
        val url = href.toHttpUrlOrNull() ?: throw CalDavException("Ungültige Event-Adresse: $href")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .delete()
            .build()
        executeHttp(request) { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw CalDavException("Nextcloud-Fehler beim Löschen (${response.code})")
            }
        }
    }

    private fun successfulProp(response: XmlNode): XmlNode? =
        response.allChildren("propstat")
            .firstOrNull { it.findDescendant("status")?.text?.contains("200") == true }
            ?.firstChild("prop")

    /**
     * Nextcloud-CalDAV liegt standardmäßig unter "/remote.php/dav/". Akzeptiert wird aber auch
     * eine bereits vollständige DAV-/Kalender-URL, falls der Nutzer die direkt einträgt.
     */
    private suspend fun resolveDavBase(serverUrl: String, username: String, password: String): HttpUrl {
        val trimmed = serverUrl.trim().trimEnd('/')
        val candidates = listOf("$trimmed/remote.php/dav/", "$trimmed/")
        for (candidate in candidates) {
            val url = candidate.toHttpUrlOrNull() ?: continue
            val reachable = runCatching { propfind(url, username, password, depth = 0, body = PRINCIPAL_BODY) }.isSuccess
            if (reachable) return url
        }
        throw CalDavException("Nextcloud/CalDAV unter \"$serverUrl\" nicht erreichbar")
    }

    private suspend fun propfindOrNull(url: HttpUrl, username: String, password: String, body: String): XmlNode? =
        runCatching { propfind(url, username, password, depth = 0, body = body) }.getOrNull()

    private suspend fun propfind(url: HttpUrl, username: String, password: String, depth: Int, body: String): XmlNode =
        request(url, "PROPFIND", username, password, depth, body)

    private suspend fun request(url: HttpUrl, method: String, username: String, password: String, depth: Int, body: String): XmlNode {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", depth.toString())
            .method(method, body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        return executeHttp(request) { response ->
            if (!response.isSuccessful) {
                throw CalDavException("Nextcloud antwortete mit ${response.code} für $method $url")
            }
            parseXmlTree(response.body?.string().orEmpty())
        }
    }

    private suspend fun <T> executeHttp(request: Request, block: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use(block)
        }

    private companion object {
        const val PRINCIPAL_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:current-user-principal/></D:prop></D:propfind>"

        const val HOME_SET_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<D:propfind xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
            "<D:prop><C:calendar-home-set/></D:prop></D:propfind>"

        const val LIST_CALENDARS_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:resourcetype/><D:displayname/></D:prop></D:propfind>"

        const val EVENT_QUERY_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<C:calendar-query xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
            "<D:prop><D:getetag/><C:calendar-data/></D:prop>" +
            "<C:filter><C:comp-filter name=\"VCALENDAR\"><C:comp-filter name=\"VEVENT\"/></C:comp-filter></C:filter>" +
            "</C:calendar-query>"
    }
}

/** Generischer, namespace-unabhängiger XML-Baum (WebDAV-Server nutzen unterschiedliche Präfixe für "DAV:"). */
private data class XmlNode(val name: String, val text: String, val children: List<XmlNode>) {
    fun firstChild(localName: String): XmlNode? = children.firstOrNull { it.name == localName }
    fun allChildren(localName: String): List<XmlNode> = children.filter { it.name == localName }
    fun findDescendant(localName: String): XmlNode? {
        firstChild(localName)?.let { return it }
        for (child in children) {
            child.findDescendant(localName)?.let { return it }
        }
        return null
    }
}

private fun parseXmlTree(xml: String): XmlNode {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(xml))
    parser.nextTag()
    return parseElement(parser)
}

private fun parseElement(parser: XmlPullParser): XmlNode {
    val name = parser.name
    val text = StringBuilder()
    val children = mutableListOf<XmlNode>()
    var eventType = parser.next()
    while (eventType != XmlPullParser.END_TAG) {
        when (eventType) {
            XmlPullParser.START_TAG -> children.add(parseElement(parser))
            XmlPullParser.TEXT -> text.append(parser.text)
        }
        eventType = parser.next()
    }
    return XmlNode(name = name, text = text.toString().trim(), children = children)
}

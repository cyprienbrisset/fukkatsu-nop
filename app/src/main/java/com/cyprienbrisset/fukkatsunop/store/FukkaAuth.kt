package com.cyprienbrisset.fukkatsunop.store

// TEMPORARY SPIKE: FukkaStore Google-account auth.
// Corrected engine replicating Aurora Store's EXACT AC2DM exchange (oauth_token -> aasToken),
// including the GMS caller params that the previous spike was missing.

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
private const val BUILD_VERSION_SDK = 28
private const val PLAY_SERVICES_VERSION_CODE = 19629032

private val httpClient = OkHttpClient()

/**
 * AC2DM exchange: oauth_token -> aasToken, replicating Aurora's AC2DMTask exactly.
 * Returns response["Token"], throws on failure.
 */
suspend fun exchangeAasToken(email: String, oauthToken: String): String = withContext(Dispatchers.IO) {
    // Params copied verbatim from Aurora's AC2DMTask.getAC2DMResponse (order-independent, url-joined k=v&...).
    val params = linkedMapOf<String, Any>(
        "lang" to Locale.getDefault().toString().replace("_", "-"),
        "google_play_services_version" to PLAY_SERVICES_VERSION_CODE,
        "sdk_version" to BUILD_VERSION_SDK,
        "device_country" to Locale.getDefault().country.lowercase(Locale.US),
        "Email" to email,
        "service" to "ac2dm",
        "get_accountid" to 1,
        "ACCESS_TOKEN" to 1,
        "callerPkg" to "com.google.android.gms",
        "add_account" to 1,
        "Token" to oauthToken,
        "callerSig" to "38918a453d07199354f8b19af05ec6562ced5788",
        "droidguard_results" to "null",
    )

    val body = params.map { "${it.key}=${it.value}" }.joinToString(separator = "&")

    val request = Request.Builder()
        .url(TOKEN_AUTH_URL)
        .addHeader("app", "com.google.android.gms")
        .addHeader("User-Agent", "")
        .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        .build()

    val raw = httpClient.newCall(request).execute().use { response ->
        String(response.body?.bytes() ?: ByteArray(0))
    }

    val map = parseKeyValueResponse(raw)
    val aasToken = map["Token"]
    if (aasToken.isNullOrBlank()) {
        // Include the raw response head for diagnostics (e.g. Error=BadAuthentication, Url=...).
        throw IllegalStateException("Could not generate AAS Token. Response head: " + raw.take(300))
    }
    aasToken
}

/**
 * Build gplayapi [AuthData] from an AAS token using the bundled device profile,
 * matching Aurora's AuthProvider.buildGoogleAuthData / AuthHelper.build signature.
 */
suspend fun buildAuthDataFromAas(context: Context, email: String, aasToken: String): AuthData =
    withContext(Dispatchers.IO) {
        val props = Properties().apply {
            context.assets.open("fukka_device.properties").use { load(it) }
        }
        // gplayapi 3.2.6 published AuthHelper.build has no tokenType enum; the 2nd arg IS the AAS
        // token: build(email, aasToken, properties, locale). Verified against the resolved jar.
        AuthHelper.build(
            email,
            aasToken,
            props,
            Locale.getDefault(),
        )
    }

/**
 * Sanity search to prove the AuthData works end-to-end. Returns display names of results.
 */
suspend fun searchTitles(authData: AuthData, query: String): List<String> = withContext(Dispatchers.IO) {
    SearchHelper(authData).searchResults(query).appList.map { it.displayName }
}

private fun com.aurora.gplayapi.data.models.App.toStoreApp() = StoreApp(
    packageName = packageName,
    title = displayName,
    developer = developerName,
    iconUrl = iconArtwork.url,
    versionCode = versionCode,
)

/**
 * Whether the app is installable on this device. We only exclude apps Play explicitly flags as
 * device-incompatible; browse/category endpoints often leave `restriction` UNKNOWN/GENERIC even
 * for perfectly compatible apps, so requiring NOT_RESTRICTED wrongly empties whole categories.
 */
private fun com.aurora.gplayapi.data.models.App.isDeviceCompatible(): Boolean =
    restriction != com.aurora.gplayapi.Constants.Restriction.DEVICE_RESTRICTED

/**
 * Search the Play catalog and map results to [StoreApp].
 * Filters out device-incompatible or restricted apps.
 */
suspend fun search(authData: AuthData, query: String): List<StoreApp> =
    withContext(Dispatchers.IO) {
        SearchHelper(authData).searchResults(query).appList
            .filter { it.isDeviceCompatible() }
            .map { it.toStoreApp() }
    }

/**
 * Curated list of top FREE apps compatible with the device profile.
 */
suspend fun topApps(authData: AuthData, limit: Int = 40): List<StoreApp> =
    withContext(Dispatchers.IO) {
        com.aurora.gplayapi.helpers.TopChartsHelper(authData)
            .getCluster(
                com.aurora.gplayapi.helpers.TopChartsHelper.Type.APPLICATION,
                com.aurora.gplayapi.helpers.TopChartsHelper.Chart.TOP_SELLING_FREE,
            )
            .clusterAppList
            .filter { it.isDeviceCompatible() }
            .map { it.toStoreApp() }
            .distinctBy { it.packageName }
            .take(limit)
    }

/** Browsable application categories (localized title). */
suspend fun categories(authData: AuthData): List<StoreCategory> =
    withContext(Dispatchers.IO) {
        com.aurora.gplayapi.helpers.CategoryHelper(authData)
            .getAllCategoriesList(com.aurora.gplayapi.data.models.Category.Type.APPLICATION)
            .filter { it.title.isNotBlank() }
            .map { StoreCategory(key = it.browseUrl.ifBlank { it.title }, title = it.title, browseUrl = it.browseUrl) }
            .distinctBy { it.key }
    }

/**
 * Apps for a category. gplayapi 3.2.6's protobuf category-browse endpoint (getSubCategoryBundle)
 * no longer returns clusters on current Play, so we resolve category apps via the working search
 * endpoint using the category name as the query.
 */
suspend fun categoryApps(authData: AuthData, query: String, limit: Int = 60): List<StoreApp> =
    withContext(Dispatchers.IO) {
        com.aurora.gplayapi.helpers.SearchHelper(authData).searchResults(query).appList
            .filter { it.isDeviceCompatible() }
            .map { it.toStoreApp() }
            .distinctBy { it.packageName }
            .take(limit)
    }

/**
 * Resolve the downloadable APK/split files for [packageName] via the Play "purchase" flow.
 * The [versionCode] argument is accepted for the public contract but the authoritative
 * version/offerType are taken from the freshly fetched app details.
 */
suspend fun files(authData: AuthData, packageName: String, versionCode: Int): List<ApkFile> =
    withContext(Dispatchers.IO) {
        val details = AppDetailsHelper(authData).getAppByPackageName(packageName)
        val gFiles = PurchaseHelper(authData)
            .purchase(details.packageName, details.versionCode, details.offerType)
        gFiles.map { ApkFile(name = it.name, url = it.url, size = it.size) }
    }

/** Full app details for the detail sheet (description, screenshots, rating). */
suspend fun appDetail(authData: AuthData, packageName: String): AppDetail =
    withContext(Dispatchers.IO) {
        val app = AppDetailsHelper(authData).getAppByPackageName(packageName)
        AppDetail(
            packageName = app.packageName,
            title = app.displayName,
            developer = app.developerName,
            iconUrl = app.iconArtwork.url,
            versionCode = app.versionCode,
            shortDescription = app.shortDescription,
            description = app.description,
            screenshotUrls = app.screenshots.map { it.url },
            ratingAverage = app.rating.average,
        )
    }

private fun parseKeyValueResponse(response: String): Map<String, String> {
    val map = HashMap<String, String>()
    response.split("\n", "\r").forEach { line ->
        if (line.isBlank()) return@forEach
        val kv = line.split("=", limit = 2)
        if (kv.size >= 2) map[kv[0]] = kv[1]
    }
    return map
}

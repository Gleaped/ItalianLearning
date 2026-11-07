package com.gleaped.italianlearning.data.dictionary

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.gleaped.italianlearning.domain.dictionary.DictionaryInstallPlan
import com.gleaped.italianlearning.domain.dictionary.DictionaryLookup
import com.gleaped.italianlearning.domain.dictionary.DictionarySql
import com.gleaped.italianlearning.domain.dictionary.DictionaryStore
import com.gleaped.italianlearning.domain.dictionary.FormMatch
import com.gleaped.italianlearning.domain.dictionary.InstallAction
import com.gleaped.italianlearning.domain.dictionary.LemmaRow
import com.gleaped.italianlearning.domain.dictionary.LookupGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface DictionaryAvailability {
    data class Ready(val lookup: DictionaryLookup, val meta: Map<String, String>) : DictionaryAvailability {
        val sources: String? get() = meta["sources"]?.takeIf { it.isNotBlank() }
    }
    /** The APK ships no dictionary.db. */
    data object NotInstalled : DictionaryAvailability
    /** The file is there but could not be copied or opened. */
    data object Broken : DictionaryAvailability
}

/**
 * The offline dictionary (assets/dictionary/dictionary.db).
 *
 * SQLite cannot open a file inside the APK, so on first use the database is copied to
 * no-backup storage (it is reproducible, so it stays out of backups). After an app update the
 * asset is copied to a temp file and swapped in only when its meta.version differs; see
 * [DictionaryInstallPlan]. The copy is opened read-only with android.database.sqlite (no Room).
 * Everything runs on [Dispatchers.IO].
 */
class DictionaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var availability: DictionaryAvailability? = null

    suspend fun availability(): DictionaryAvailability {
        availability?.let { return it }
        return mutex.withLock {
            availability ?: withContext(Dispatchers.IO) { openOrInstall() }.also { availability = it }
        }
    }

    suspend fun search(query: String, maxGroups: Int = DictionaryLookup.MAX_GROUPS): List<LookupGroup> {
        val ready = availability() as? DictionaryAvailability.Ready ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                ready.lookup.search(query, maxGroups)
            } catch (e: SQLiteException) {
                Log.w(TAG, "lookup failed", e)
                emptyList()
            }
        }
    }

    suspend fun entry(id: Long): LemmaRow? {
        val ready = availability() as? DictionaryAvailability.Ready ?: return null
        return withContext(Dispatchers.IO) {
            try {
                ready.lookup.entry(id)
            } catch (e: SQLiteException) {
                Log.w(TAG, "entry $id failed", e)
                null
            }
        }
    }

    // ------------------------------------------------------------ install

    private fun openOrInstall(): DictionaryAvailability {
        if (!assetExists()) return DictionaryAvailability.NotInstalled
        val dir = File(appContext.noBackupFilesDir, "dictionary").apply { mkdirs() }
        val target = File(dir, DB_NAME)
        val stampFile = File(dir, STAMP_NAME)
        return try {
            val installedVersion = if (target.isFile) readVersion(target) else null
            val stamp = apkStamp()
            val installedStamp = if (stampFile.isFile) stampFile.readText().trim() else null
            when (DictionaryInstallPlan.decide(installedVersion, installedStamp, stamp)) {
                InstallAction.USE_INSTALLED -> Unit
                InstallAction.COPY -> {
                    val tmp = copyAsset(dir)
                    if (readVersion(tmp) == null) {
                        tmp.delete()
                        return DictionaryAvailability.Broken
                    }
                    replace(tmp, target)
                    stampFile.writeText(stamp)
                }
                InstallAction.COPY_AND_COMPARE -> {
                    val tmp = copyAsset(dir)
                    val assetVersion = readVersion(tmp)
                    if (DictionaryInstallPlan.shouldReplace(installedVersion, assetVersion)) {
                        replace(tmp, target)
                    } else {
                        tmp.delete()
                    }
                    stampFile.writeText(stamp)
                }
            }
            val db = SQLiteDatabase.openDatabase(
                target.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            val store = SqliteDictionaryStore(db)
            DictionaryAvailability.Ready(DictionaryLookup(store), store.meta())
        } catch (e: IOException) {
            Log.w(TAG, "dictionary copy failed", e)
            DictionaryAvailability.Broken
        } catch (e: SQLiteException) {
            Log.w(TAG, "dictionary open failed", e)
            DictionaryAvailability.Broken
        }
    }

    private fun assetExists(): Boolean = try {
        appContext.assets.list(ASSET_DIR)?.contains(DB_NAME) == true
    } catch (_: IOException) {
        false
    }

    /** Changes with every install or update of the APK. */
    private fun apkStamp(): String {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return "${PackageInfoCompat.getLongVersionCode(info)}-${info.lastUpdateTime}"
    }

    private fun copyAsset(dir: File): File {
        val tmp = File(dir, "$DB_NAME.tmp")
        appContext.assets.open("$ASSET_DIR/$DB_NAME").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return tmp
    }

    private fun replace(tmp: File, target: File) {
        if (target.exists() && !target.delete()) throw IOException("cannot remove old dictionary")
        if (!tmp.renameTo(target)) throw IOException("cannot move dictionary into place")
    }

    /** meta.version of a database file, or null when it cannot be read. */
    private fun readVersion(file: File): String? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            .use { db ->
                db.rawQuery("SELECT value FROM meta WHERE key = 'version'", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }
    } catch (e: SQLiteException) {
        Log.w(TAG, "cannot read dictionary version", e)
        null
    }

    private companion object {
        const val TAG = "DictionaryRepository"
        const val ASSET_DIR = "dictionary"
        const val DB_NAME = "dictionary.db"
        const val STAMP_NAME = "installed.stamp"
    }
}

/** [DictionaryStore] over a read-only SQLiteDatabase, running the SQL in [DictionarySql]. */
private class SqliteDictionaryStore(private val db: SQLiteDatabase) : DictionaryStore {

    override fun formsByNorm(norm: String, limit: Int): List<Pair<FormMatch, LemmaRow>> =
        db.rawQuery(DictionarySql.FORMS_BY_NORM, arrayOf(norm, limit.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val lemma = c.lemmaAt(DictionarySql.FORMS_LEMMA_OFFSET)
                    add(FormMatch(form = c.getString(0).orEmpty(), tags = c.getString(1).orEmpty(), lemmaId = lemma.id) to lemma)
                }
            }
        }

    override fun lemmasInRange(lower: String, upper: String, limit: Int): List<LemmaRow> =
        db.rawQuery(DictionarySql.LEMMAS_IN_RANGE, arrayOf(lower, upper, limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.lemmaAt(0)) }
        }

    override fun lemma(id: Long): LemmaRow? =
        db.rawQuery(DictionarySql.LEMMA_BY_ID, arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.lemmaAt(0) else null
        }

    override fun meta(): Map<String, String> =
        db.rawQuery(DictionarySql.META, null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
        }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

    /** Reads the lemma columns (in DictionarySql order) starting at column [o]. */
    private fun Cursor.lemmaAt(o: Int) = LemmaRow(
        id = getLong(o),
        lemma = str(o + 1).orEmpty(),
        pos = str(o + 2).orEmpty(),
        gender = str(o + 3),
        glossEn = str(o + 4).orEmpty(),
        glossIt = str(o + 5),
        exampleIt = str(o + 6),
        exampleEn = str(o + 7),
        freqRank = if (isNull(o + 8)) null else getInt(o + 8),
        notes = str(o + 9),
        formsJson = str(o + 10) ?: "{}",
    )
}

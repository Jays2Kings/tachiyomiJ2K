package eu.kanade.tachiyomi.extension.repository

import android.database.sqlite.SQLiteConstraintException
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_BASE_URL
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_SIGNING_KEY_FINGERPRINT
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.TABLE
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.extension.repository.exception.SaveExtensionRepoException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Implementation of the [ExtensionRepoRepository] interface using Storio.
 * Manages reactivity manually using a StateFlow.
 */
class ExtensionRepoRepositoryImpl(
    private val dbProvider: DbProvider = Injekt.get(),
) : ExtensionRepoRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: StorIOSQLite get() = dbProvider.db

    private val _reposFlow = MutableStateFlow<List<ExtensionRepo>>(emptyList())

    init {
        scope.launch {
            _reposFlow.value = getAll()
        }
    }

    private suspend fun updateRepoFlow() {
        _reposFlow.value = getAll()
    }

    override fun subscribeAll(): Flow<List<ExtensionRepo>> = _reposFlow.asStateFlow()

    override suspend fun getAll(): List<ExtensionRepo> =
        db
            .get()
            .listOfObjects(ExtensionRepo::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(TABLE)
                    .build(),
            ).prepare()
            .executeAsBlocking()

    override suspend fun getRepository(baseUrl: String): ExtensionRepo? =
        db
            .get()
            .`object`(ExtensionRepo::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(TABLE)
                    .where("$COL_BASE_URL = ?")
                    .whereArgs(baseUrl)
                    .limit(1)
                    .build(),
            ).prepare()
            .executeAsBlocking()

    override suspend fun getRepositoryBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? =
        db
            .get()
            .`object`(ExtensionRepo::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(TABLE)
                    .where("$COL_SIGNING_KEY_FINGERPRINT = ?")
                    .whereArgs(fingerprint)
                    .limit(1)
                    .build(),
            ).prepare()
            .executeAsBlocking()

    override fun getCount(): Flow<Int> = _reposFlow.map { it.size }

    override suspend fun insertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        val repo = ExtensionRepo(baseUrl, name, shortName, website, signingKeyFingerprint)
        try {
            db
                .put()
                .`object`(repo)
                .prepare()
                .executeAsBlocking()
            scope.launch { updateRepoFlow() }
        } catch (e: SQLiteConstraintException) {
            throw SaveExtensionRepoException(e)
        }
    }

    override suspend fun upsertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        val repo = ExtensionRepo(baseUrl, name, shortName, website, signingKeyFingerprint)
        try {
            db
                .put()
                .`object`(repo)
                .prepare()
                .executeAsBlocking()
            scope.launch { updateRepoFlow() }
        } catch (e: SQLiteConstraintException) {
            throw SaveExtensionRepoException(e)
        }
    }

    override suspend fun replaceRepository(newRepo: ExtensionRepo) {
        try {
            db
                .put()
                .`object`(newRepo)
                .prepare()
                .executeAsBlocking()
            scope.launch { updateRepoFlow() }
        } catch (e: SQLiteConstraintException) {
            throw SaveExtensionRepoException(e)
        }
    }

    override suspend fun deleteRepository(baseUrl: String) {
        db
            .delete()
            .byQuery(
                DeleteQuery
                    .builder()
                    .table(TABLE)
                    .where("$COL_BASE_URL = ?")
                    .whereArgs(baseUrl)
                    .build(),
            ).prepare()
            .executeAsBlocking()
        scope.launch { updateRepoFlow() }
    }
}

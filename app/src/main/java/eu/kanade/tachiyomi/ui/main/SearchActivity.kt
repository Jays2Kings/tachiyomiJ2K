package eu.kanade.tachiyomi.ui.main

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.synthetic.main.main_activity.*

class SearchActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolbar?.navigationIcon = drawerArrow
        toolbar?.setNavigationOnClickListener {
            popToRoot()
        }
    }

    override fun onBackPressed() {
        if (router.backstack.size <= 1 || !router.handleBack()) {
            SecureActivityDelegate.locked = true
            super.onBackPressed()
        }
    }

    private fun popToRoot() {
        if (intent.action == SHORTCUT_MANGA) {
            onBackPressed()
        } else if (!router.handleBack()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finishAfterTransition()
        }
    }

    override fun syncActivityViewWithController(
        to: Controller?,
        from: Controller?,
        isPush:
            Boolean
    ) {
        if (from is DialogController || to is DialogController) {
            return
        }
        toolbar.navigationIcon = drawerArrow
        drawerArrow?.progress = 1f

        bottom_nav.gone()
    }

    override fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) NotificationReceiver.dismissNotification(
            applicationContext,
            notificationId,
            intent.getIntExtra("groupId", 0)
        )
        when (intent.action) {
            Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY)
                if (query != null && query.isNotEmpty()) {
                    router.replaceTopController(GlobalSearchController(query).withFadeTransaction())
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                if (query != null && query.isNotEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.replaceTopController(GlobalSearchController(query, filter).withFadeTransaction())
                }
            }
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                router.replaceTopController(
                    RouterTransaction.with(MangaDetailsController(extras))
                        .pushChangeHandler(SimpleSwapChangeHandler())
                        .popChangeHandler(FadeChangeHandler())
                )
            }
            else -> return false
        }
        return true
    }

    companion object {
        fun openMangaIntent(context: Context, id: Long) = Intent(
            context,
            SearchActivity::class
                .java
        )
            .apply {
                action = SHORTCUT_MANGA
                putExtra(MangaDetailsController.MANGA_EXTRA, id)
            }
    }
}

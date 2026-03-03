package eu.kanade.tachiyomi.androidapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.bootstrap.initializeAndroidAppBootstrap

class MainActivity : AppCompatActivity() {

    private val contracts by lazy { initializeAndroidAppBootstrap(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contracts.platformHttpClientFactory.newBuilder()
        setContentView(android.R.layout.simple_list_item_1)
    }
}

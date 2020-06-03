package com.lanecki.deepnoise

import android.app.SearchManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                searchUsers(query)
            }
        }
    }

    private fun searchUsers(query: String) {
        // TODO:
        //  - call server and suspend for results
        //  - progress dialog? (https://developer.android.com/guide/topics/ui/dialogs#ProgressDialog)
        //  - upon results reception place them in the VM (view auto-update)
    }
}

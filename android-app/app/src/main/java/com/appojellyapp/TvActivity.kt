package com.appojellyapp

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.appojellyapp.tv.home.TvHomeFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, TvHomeFragment())
                .commit()
        }
    }
}

package com.example.rtscaleversion10

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        var toolbar: Toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar)

    }
}



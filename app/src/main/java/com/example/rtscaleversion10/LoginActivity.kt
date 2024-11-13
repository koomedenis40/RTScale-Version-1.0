package com.example.rtscaleversion10

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.rtscaleversion10.databinding.LoginActivityBinding


class LoginActivity : Activity(){

    private lateinit var binding: LoginActivityBinding

    lateinit var username: EditText
    lateinit var password: EditText
    lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        binding = LoginActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Login  to the Application
        binding.loginButton.setOnClickListener(View.OnClickListener{
            if (binding.username.text.toString().equals("admin") && binding.ipassword.text.toString().equals("admin")) {
                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()

                // Navigate to the main screen
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show()
            }
        })



        // Register the Application
    }
}
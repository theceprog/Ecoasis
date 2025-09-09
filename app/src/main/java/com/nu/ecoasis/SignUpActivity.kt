package com.nu.ecoasis

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Date

class SignUpActivity : AppCompatActivity() {

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var termsCheckbox: CheckBox
    private lateinit var signUpButton: Button
    private lateinit var loginLink: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_sign_up)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            insets
        }

        firstNameEditText = findViewById(R.id.firstnamefield)
        lastNameEditText = findViewById(R.id.lastnamefield)
        emailEditText = findViewById(R.id.emailfield)
        usernameEditText = findViewById(R.id.usernamefield)
        passwordEditText = findViewById(R.id.passwordfield)
        confirmPasswordEditText = findViewById(R.id.confirmpasswordfield)
        termsCheckbox = findViewById(R.id.termscheckbox)
        signUpButton = findViewById(R.id.signupbutton)
        loginLink = findViewById(R.id.loginlink)
        progressBar = findViewById(R.id.progressBar)

        signUpButton.setOnClickListener {
            registerUser()
        }

        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun registerUser() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()
        val agreedToTerms = termsCheckbox.isChecked

        // Validate input
        if (firstName.isEmpty()) {
            firstNameEditText.error = "First name is required"
            firstNameEditText.requestFocus()
            return
        }

        if (lastName.isEmpty()) {
            lastNameEditText.error = "Last name is required"
            lastNameEditText.requestFocus()
            return
        }

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            emailEditText.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Please enter a valid email"
            emailEditText.requestFocus()
            return
        }

        if (username.isEmpty()) {
            usernameEditText.error = "Username is required"
            usernameEditText.requestFocus()
            return
        }

        if (username.length < 3) {
            usernameEditText.error = "Username must be at least 3 characters"
            usernameEditText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            passwordEditText.requestFocus()
            return
        }

        if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            passwordEditText.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "Please confirm your password"
            confirmPasswordEditText.requestFocus()
            return
        }

        if (password != confirmPassword) {
            confirmPasswordEditText.error = "Passwords do not match"
            confirmPasswordEditText.requestFocus()
            return
        }

        if (!agreedToTerms) {
            Toast.makeText(
                this,
                "You must agree to the Terms of Service and Privacy Policy",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Show progress bar
        progressBar.visibility = View.VISIBLE

        // Use FirestoreManager to register user
        FirestoreManager.registerUser(
            email = email,
            firstName = firstName,
            lastName = lastName,
            username = username,
            password = password,
            onSuccess = {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()

                // Redirect to LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
                finish()
            },
            onFailure = { exception ->
                progressBar.visibility = View.GONE
                when (exception.message) {
                    "Email already registered" -> {
                        emailEditText.error = exception.message
                        emailEditText.requestFocus()
                    }
                    "Username already taken" -> {
                        usernameEditText.error = exception.message
                        usernameEditText.requestFocus()
                    }
                    else -> {
                        Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
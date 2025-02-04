package io.qonversion.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.qonversion.android.sdk.Qonversion

private const val CODE_GOOGLE_AUTH = 5491

class AuthActivity : AppCompatActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.firebase_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        firebaseAuth = Firebase.auth

        val loginButton = findViewById<MaterialButton>(R.id.buttonLogin)
        loginButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, CODE_GOOGLE_AUTH)
        }

        val skipButton = findViewById<MaterialButton>(R.id.buttonSkip)
        skipButton.setOnClickListener {
            goToMainFlow()
        }
    }

    override fun onStart() {
        super.onStart()
        firebaseAuth.currentUser?.let {
            goToMainFlow(it.uid)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CODE_GOOGLE_AUTH) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showGoogleLoginError()
            }
        }
    }

    private fun showGoogleLoginError() {
        Toast.makeText(this, "Failed to login with Google", Toast.LENGTH_SHORT).show()
    }

    private fun goToMainFlow(userId: String? = null) {
        userId?.let { Qonversion.shared.identify(userId) }
        val intent = MainActivity.getCallingIntent(this)
        startActivity(intent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                showGoogleLoginError()
                return@addOnCompleteListener
            }

            firebaseAuth.currentUser?.let {
                // Sign out from google to disable auto-auth on next attempt.
                googleSignInClient.signOut()
                goToMainFlow(it.uid)
            } ?: showGoogleLoginError()
        }
    }

    companion object {
        fun getCallingIntent(context: Context): Intent {
            return Intent(context, AuthActivity::class.java)
        }
    }
}
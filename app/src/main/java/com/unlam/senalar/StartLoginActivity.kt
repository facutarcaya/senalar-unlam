package com.unlam.senalar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import com.unlam.senalar.databinding.ActivityStartLoginBinding
import com.unlam.senalar.helpers.PreferencesHelper
import com.unlam.senalar.helpers.SubscribedUser


class StartLoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var database: FirebaseDatabase

    // Preferences variables
    private lateinit var preferencesHelper: PreferencesHelper

    private lateinit var binding : ActivityStartLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityStartLoginBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Init database
        database = FirebaseDatabase.getInstance()

        // Initialize preferences
        preferencesHelper = PreferencesHelper(this.applicationContext)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.your_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        auth = Firebase.auth

        binding.btnClose.setOnClickListener {
            this.finish()
        }

        binding.btnSubscribe.setOnClickListener {
            startActivity(Intent(this, StartSubscription::class.java))
        }

        binding.btnCancelSubscribe.setOnClickListener {
            val builder = AlertDialog.Builder(this)

            with(builder)
            {
                setTitle("Cancelar subscripción")
                setMessage("¿Está seguro que desea cancelar la subscripción?")
                setPositiveButton("ACEPTAR") { dialogInterface, i ->
                    auth.currentUser?.uid?.let {
                        database.getReference(DATABASE_NAME).child(DATABASE_USERS_FIELD).child(it).removeValue().addOnSuccessListener {
                            preferencesHelper.setBooleanPreference(PreferencesHelper.IS_USER_SUBSCRIBED, false)
                            updateUI(auth.currentUser)
                        }
                    }
                }
                setNegativeButton("CANCELAR") {dialogInterface, i -> /** DO NOTHING*/}
                show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun searchSubscription(user: FirebaseUser) {
        user.email?.let { email ->
            database.getReference(DATABASE_NAME).child(DATABASE_USERS_FIELD).child(user.uid).get().addOnSuccessListener {
                try {
                    val subscribedUser = Gson().fromJson(it.value.toString(), SubscribedUser::class.java)
                    subscribedUser.email?.let {
                        preferencesHelper.setBooleanPreference(PreferencesHelper.IS_USER_SUBSCRIBED, true)
                        preferencesHelper.setStringPreference(PreferencesHelper.EMAIL_SUBSCRIPTION, it)
                    }
                    subscribedUser.creditCardDigits?.let {
                        preferencesHelper.setStringPreference(PreferencesHelper.CREDIT_CARD_SUBSCRIPTION, it)
                    }
                } catch (e: Exception) {
                    Log.e("firebase", "Error decrypting user", e)
                }
            }.addOnFailureListener{
                Log.e("firebase", "Error getting data", it)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Error al entrar con Google. Intente nuevamente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Error al entrar con Google. Intente nuevamente", Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signOut() {
        googleSignInClient.signOut()
            .addOnCompleteListener(this) {
                auth.signOut()
                updateUI(null)
                preferencesHelper.setBooleanPreference(PreferencesHelper.IS_USER_SUBSCRIBED, false)
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        user?.let {
            binding.titleName.visibility = View.VISIBLE
            binding.titleName.text = user.displayName
            Picasso.get().load(user.photoUrl).into(binding.profilePicture)
            binding.signInOutText.text = getString(R.string.sign_out)
            binding.signInOutLayout.background = getDrawable(R.color.pause_red)
            binding.googleIcon.visibility = View.GONE
            binding.signin.setOnClickListener {
                signOut()
            }

            searchSubscription(it)

            if (preferencesHelper.getBooleanPreference(PreferencesHelper.IS_USER_SUBSCRIBED) &&
                    preferencesHelper.getStringPreference(PreferencesHelper.EMAIL_SUBSCRIPTION) == user.email) { // TODO Check subscribe
                binding.btnSubscribe.visibility = View.GONE
                binding.btnCancelSubscribe.visibility = View.VISIBLE
                binding.tvSubscribeDescription.visibility = View.VISIBLE
                binding.tvSubscribeDescription.text =
                    "Subscrito a versión premium con tarjeta **** ${preferencesHelper.getStringPreference(PreferencesHelper.CREDIT_CARD_SUBSCRIPTION)}"
            } else {
                binding.tvSubscribeDescription.visibility = View.GONE
                binding.btnCancelSubscribe.visibility = View.GONE
                binding.btnSubscribe.visibility = View.VISIBLE
            }

            return
        }
        binding.btnSubscribe.visibility = View.GONE
        binding.btnCancelSubscribe.visibility = View.GONE
        binding.tvSubscribeDescription.visibility = View.GONE
        binding.titleName.visibility = View.GONE
        binding.profilePicture.setImageDrawable(getDrawable(R.drawable.ic_baseline_person_24))
        binding.signInOutText.text = getString(R.string.sign_in_with_google)
        binding.signInOutLayout.background = getDrawable(R.color.contrast_light_blue)
        binding.googleIcon.visibility = View.VISIBLE
        binding.signin.setOnClickListener {
            signIn()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    companion object {
        private const val TAG = "GoogleActivity"
        private const val DATABASE_NAME = "subscribed_users"
        private const val DATABASE_USERS_FIELD = "users"
        private const val RC_SIGN_IN = 9001
    }
}
package com.unlam.senalar


import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.braintreepayments.cardform.view.CardForm
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.unlam.senalar.databinding.ActivityStartSubscriptionBinding
import com.unlam.senalar.helpers.PreferencesHelper
import com.unlam.senalar.helpers.SubscribedUser


class StartSubscription : AppCompatActivity() {
    private lateinit var binding: ActivityStartSubscriptionBinding
    private lateinit var database: FirebaseDatabase

    private lateinit var auth: FirebaseAuth

    // Preferences variables
    private lateinit var preferencesHelper: PreferencesHelper

    private lateinit var cardForm : CardForm

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityStartSubscriptionBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Initialize preferences
        preferencesHelper = PreferencesHelper(this.applicationContext)

        // Init database
        database = FirebaseDatabase.getInstance()

        auth = Firebase.auth

        cardForm = binding.cardForm
        cardForm.cardRequired(true)
            .expirationRequired(true)
            .cvvRequired(true)
            .cardholderName(CardForm.FIELD_REQUIRED)
            .postalCodeRequired(false)
            .mobileNumberRequired(false)
            .actionLabel("Subscribirse")
            .setup(this)

        cardForm.setOnCardFormSubmitListener {
            subscribe()
        }

        cardForm.setOnCardFormValidListener { valid ->
            binding.btnSubscribe.isEnabled = valid
            if (valid) {
                binding.btnSubscribe.visibility = View.VISIBLE
                binding.btnSubscribeDummy.visibility = View.GONE
            } else {
                binding.btnSubscribe.visibility = View.GONE
                binding.btnSubscribeDummy.visibility = View.VISIBLE
            }
        }

        binding.btnSubscribe.setOnClickListener {
            subscribe()
        }
    }

    private fun subscribe() {
        auth.currentUser?.let {
            writeUser(it)
        }
        if (auth.currentUser?.email == null) {
            Toast.makeText(this, "Error al activar la subscripción, intente nuevamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeUser(user: FirebaseUser) {
        database.getReference(DATABASE_NAME).child(DATABASE_USERS_FIELD).child(user.uid).setValue(
            SubscribedUser(cardForm.cardNumber.takeLast(4), user.email)
        ).addOnSuccessListener {
            user.email?.let {
                preferencesHelper.setStringPreference(PreferencesHelper.EMAIL_SUBSCRIPTION, it)
            }
            preferencesHelper.setStringPreference(PreferencesHelper.CREDIT_CARD_SUBSCRIPTION, cardForm.cardNumber.takeLast(4))
            preferencesHelper.setBooleanPreference(PreferencesHelper.IS_USER_SUBSCRIBED, true)
            Toast.makeText(this, "Subscripción activada correctamente", Toast.LENGTH_SHORT).show()
            this.finish()
        }
    }

    companion object {
        private const val DATABASE_NAME = "subscribed_users"
        private const val DATABASE_USERS_FIELD = "users"
    }
}
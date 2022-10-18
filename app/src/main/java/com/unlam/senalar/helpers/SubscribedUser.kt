package com.unlam.senalar.helpers

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class SubscribedUser(val user_id: String? = null, val email: String? = null, val creditCardDigits: String? = null) {
    // Null default values create a no-argument default constructor, which is needed
    // for deserialization from a DataSnapshot.
}
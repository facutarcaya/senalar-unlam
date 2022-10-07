package com.unlam.senalar.helpers

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Predictions (
    @SerializedName("predictions") @Expose val predictions: Map<String, Map<String, String>>? = null,
    @SerializedName("replacements") @Expose val replacements: Map<String, String>? = null,
    @SerializedName("sentences") @Expose val sentences: Map<String, String>? = null
)
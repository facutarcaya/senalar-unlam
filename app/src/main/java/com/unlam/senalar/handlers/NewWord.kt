package com.unlam.senalar.handlers

data class NewWord(
    var word: String,
    var deletable: Boolean // Also says if it's stop sign
)

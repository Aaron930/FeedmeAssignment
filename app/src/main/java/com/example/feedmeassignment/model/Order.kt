package com.example.feedmeassignment.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf


data class Order(
    val ID: Int,
    var isVip: Boolean,
    var content: String = "",
    var isProcessing: Boolean = false,
    private val _isComplete: MutableState<Boolean> = mutableStateOf(false)
){
    var isComplete: Boolean
        get() = _isComplete.value
        set(value) {
            _isComplete.value = value
        }
}


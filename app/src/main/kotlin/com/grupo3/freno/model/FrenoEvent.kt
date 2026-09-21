package com.grupo3.freno.model

data class FrenoEvent(
    val id: String,
    val source: String,
    val sender: String,
    val preview: String,
    val reason: String,
    val whenLabel: String,
    val trusted: Boolean = false,
)

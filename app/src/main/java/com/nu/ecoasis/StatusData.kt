package com.nu.ecoasis

data class StatusData(
    val pumpa: Boolean = false,
    val pumpb: Boolean = false,
    val pumpdown: Boolean = false,
    val pumpup: Boolean = false

    ) {
    // Empty constructor for Firestore
    constructor() : this(false,false,false,false)
}


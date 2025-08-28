package com.nu.ecoasis

data class SensorData(
    val air: Double = 0.0,
    val h2o: Double = 0.0,
    val humid: Double = 0.0,
    val lux: Double = 0.0,
    val ph: Double = 0.0,
    val ppm: Int = 0,
    val up: Double = 0.0,
    val down: Double = 0.0,
    val a: Double = 0.0,
    val b: Double = 0.0
    ) {
    // Empty constructor for Firestore
    constructor() : this(0.0, 0.0, 0.0, 0.0,0.0,0,0.0,0.0,0.0,0.0)
}

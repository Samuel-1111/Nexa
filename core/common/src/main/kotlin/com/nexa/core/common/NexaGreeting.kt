package com.nexa.core.common

import java.time.ZoneId

/** Time-aware greeting shared by NEXA surfaces and notifications. */
fun greetingFor(instant: java.time.Instant, name: String): String {
    val hour = instant.atZone(ZoneId.systemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning, $name"
        in 12..16 -> "Good afternoon, $name"
        in 17..21 -> "Good evening, $name"
        else -> "Good night, $name"
    }
}

fun greetingForNow(name: String): String = greetingFor(SystemClock.now(), name)

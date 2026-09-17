package com.nexa.core.common

import java.time.Instant

fun interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

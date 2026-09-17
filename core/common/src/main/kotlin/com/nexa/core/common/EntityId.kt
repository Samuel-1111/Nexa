package com.nexa.core.common

import java.util.UUID

@JvmInline
value class EntityId(val value: String) {
    companion object {
        fun new(): EntityId = EntityId(UUID.randomUUID().toString())
    }
}

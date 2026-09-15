package com.abdulkus.essentialremap.setup

enum class SetupAccessMode {
    NON_ROOT,
    ROOT,
    ;

    companion object {
        fun fromStored(value: String?): SetupAccessMode =
            entries.firstOrNull { it.name == value } ?: NON_ROOT
    }
}

package com.example.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val isProtected: Boolean,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
)

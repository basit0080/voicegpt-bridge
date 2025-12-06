package com.basit.voicegpt

import com.basit.voicegpt.BuildConfig

object ApiKeys {
    // BuildConfig se key aayegi (Gradle inject karega)
    val OPENAI_API_KEY: String = BuildConfig.OPENAI_API_KEY
}

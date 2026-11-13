package com.example.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

  fun wrapContext(base: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
  }
}

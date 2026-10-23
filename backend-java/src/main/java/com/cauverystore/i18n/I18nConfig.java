package com.cauverystore.i18n;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduled jobs (used by the 30-minute search-index rebuild). Harmless if already enabled elsewhere. */
@Configuration
@EnableScheduling
public class I18nConfig {
}

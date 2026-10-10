package com.todoapp.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.cache.*} block.
 *
 * @param failOpen when true, a Redis outage degrades to hitting Postgres
 *                 instead of failing the request. A cache must never be able to
 *                 take the application down.
 * @param todoTtl safety net only. Correctness comes from evicting on write.
 * @param statsTtl shorter than the list because counts go stale faster and are
 *                 cheap to recompute.
 */
@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(boolean failOpen, Duration todoTtl, Duration statsTtl) {
}

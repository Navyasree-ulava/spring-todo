package com.todoapp.cache;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Boot 4 ships Jackson 3, which lives in the tools.jackson namespace.
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import com.todoapp.todo.dto.TodoResponse;
import com.todoapp.todo.dto.TodoStatsResponse;

/**
 * Cache-aside wrapper around Redis for per-user todo data.
 *
 * <p><b>Why only two keys per user.</b> The list endpoint accepts search, status,
 * priority and sort. Caching every combination would mean a single write has to
 * invalidate a keyspace it cannot enumerate without SCAN, and a single missed
 * eviction serves stale data. So only the unfiltered default view is cached -
 * one key per user, trivial to invalidate, and it covers the overwhelming
 * majority of reads. Filtered queries deliberately go to Postgres.
 *
 * <p><b>Values are DTOs, not entities.</b> The {@code Todo} entity holds a lazy
 * {@code User} reference; serialising it would drag the owner into the payload
 * and risk a lazy-load failure inside Redis.
 *
 * <p><b>Every operation fails open.</b> If Redis is unreachable we log once and
 * fall through to Postgres. A cache outage should slow the app down, not break
 * it.
 */
@Service
public class TodoCache {

	private static final Logger log = LoggerFactory.getLogger(TodoCache.class);

	private static final String LIST_PREFIX = "todo:list:";
	private static final String STATS_PREFIX = "todo:stats:";

	/**
	 * When Redis is marked down, stay quiet for this long before letting one
	 * request probe again. A sticky "down" flag would mean a restarted Redis is
	 * never noticed, and a flag that clears instantly would put every request
	 * back on the timeout path.
	 */
	private static final Duration PROBE_INTERVAL = Duration.ofSeconds(30);

	/** Suppresses retry attempts and log spam once Redis is known to be down. */
	private volatile boolean redisDown = false;
	private volatile long redisDownSince = 0L;

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final AppCacheProperties properties;

	public TodoCache(StringRedisTemplate redis, ObjectMapper objectMapper, AppCacheProperties properties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	// ---- list -------------------------------------------------------------

	public Optional<java.util.List<TodoResponse>> getList(Long userId) {
		Optional<JsonNode> cached = read(LIST_PREFIX + userId);
		if (cached.isEmpty()) {
			return Optional.empty();
		}
		try {
			TodoResponse[] parsed = this.objectMapper.treeToValue(cached.get(), TodoResponse[].class);
			return Optional.of(java.util.Arrays.asList(parsed));
		}
		catch (Exception ex) {
			// A payload written by an older build may no longer deserialise.
			// Treat it as a miss rather than a failure.
			log.warn("Discarding unreadable cached list for user {}: {}", userId, ex.getMessage());
			evictList(userId);
			return Optional.empty();
		}
	}

	public void putList(Long userId, java.util.List<TodoResponse> todos) {
		write(LIST_PREFIX + userId, todos, this.properties.todoTtl());
	}

	// ---- stats ------------------------------------------------------------

	public Optional<TodoStatsResponse> getStats(Long userId) {
		Optional<JsonNode> cached = read(STATS_PREFIX + userId);
		if (cached.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(this.objectMapper.treeToValue(cached.get(), TodoStatsResponse.class));
		}
		catch (Exception ex) {
			log.warn("Discarding unreadable cached stats for user {}: {}", userId, ex.getMessage());
			evictStats(userId);
			return Optional.empty();
		}
	}

	public void putStats(Long userId, TodoStatsResponse stats) {
		write(STATS_PREFIX + userId, stats, this.properties.statsTtl());
	}

	// ---- invalidation -----------------------------------------------------

	/**
	 * Invalidates a user's cached data <b>after the current transaction commits</b>.
	 *
	 * <p>This is the single most important method here. Evicting inside the
	 * transaction creates a permanent stale-cache bug:
	 *
	 * <ol>
	 * <li>Writer inserts a todo, DB not yet committed.</li>
	 * <li>Writer evicts the key.</li>
	 * <li>A concurrent reader misses, queries Postgres, does <em>not</em> see the
	 *     uncommitted row, and repopulates the cache with pre-commit data.</li>
	 * <li>The writer commits. The cache now holds stale data with no key left to
	 *     evict - a dirty read that persists until TTL.</li>
	 * </ol>
	 *
	 * Deferring to afterCommit closes that window: nothing can repopulate the
	 * cache from a read that predates the commit.
	 */
	public void evictAfterCommit(Long userId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// No transaction in progress - the write is already visible, so evict now.
			evict(userId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evict(userId);
			}

			@Override
			public void afterCompletion(int status) {
				// Rolled back: any earlier eviction would be wrong to keep. The write
				// never happened, so the cache is still accurate - but re-evicting is
				// harmless and guards against an eviction racing the rollback.
				if (status != STATUS_COMMITTED) {
					log.debug("Transaction rolled back for user {}; cache left untouched", userId);
				}
			}
		});
	}

	public void evict(Long userId) {
		evictList(userId);
		evictStats(userId);
	}

	public void evictList(Long userId) {
		delete(LIST_PREFIX + userId);
	}

	public void evictStats(Long userId) {
		delete(STATS_PREFIX + userId);
	}

	// ---- plumbing ---------------------------------------------------------

	private Optional<JsonNode> read(String key) {
		if (isUnavailable()) {
			return Optional.empty();
		}
		try {
			String raw = this.redis.opsForValue().get(key);
			if (raw == null) {
				return Optional.empty();
			}
			return Optional.of(this.objectMapper.readTree(raw));
		}
		catch (RedisConnectionFailureException ex) {
			markDown(ex);
			return Optional.empty();
		}
		catch (Exception ex) {
			// Anything else reaching here is also a Redis outage - Lettice surfaces
			// a dead server as a command timeout, not a connection failure. Treating
			// only one of those as "down" meant every request kept paying the
			// timeout, making the app slower than having no cache at all.
			markDown(ex);
			return Optional.empty();
		}
	}

	private void write(String key, Object value, Duration ttl) {
		if (isUnavailable()) {
			return;
		}
		try {
			this.redis.opsForValue().set(key, this.objectMapper.writeValueAsString(value), ttl);
		}
		catch (RedisConnectionFailureException ex) {
			markDown(ex);
		}
		catch (Exception ex) {
			markDown(ex);
		}
	}

	private void delete(String key) {
		if (isUnavailable()) {
			return;
		}
		try {
			this.redis.delete(key);
		}
		catch (RedisConnectionFailureException ex) {
			markDown(ex);
		}
		catch (Exception ex) {
			markDown(ex);
		}
	}

	/**
	 * True while we should skip Redis entirely. Every {@value #PROBE_INTERVAL}
	 * seconds one caller is let through to test whether Redis has recovered, so a
	 * restarted Redis resumes caching on its own with no restart of the app.
	 */
	private boolean isUnavailable() {
		if (!this.properties.failOpen() || !this.redisDown) {
			return false;
		}
		boolean probeDue = (System.currentTimeMillis() - this.redisDownSince) >= PROBE_INTERVAL.toMillis();
		if (!probeDue) {
			return true;
		}
		// Let exactly one caller probe: re-arm the window before the attempt so
		// concurrent requests do not all pile onto a dead server together.
		this.redisDownSince = System.currentTimeMillis();
		return false;
	}

	private void markDown(Exception ex) {
		if (!this.properties.failOpen()) {
			return;
		}
		boolean wasDown = this.redisDown;
		this.redisDown = true;
		this.redisDownSince = System.currentTimeMillis();
		if (!wasDown) {
			log.warn("""
					Redis is unreachable ({}). Reading straight from Postgres for now - \
					requests still succeed, they are just slower. Retried every {}s.""",
					ex.getMessage(), PROBE_INTERVAL.toSeconds());
		}
	}

}

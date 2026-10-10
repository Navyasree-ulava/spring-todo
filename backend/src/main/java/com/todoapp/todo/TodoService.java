package com.todoapp.todo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todoapp.cache.TodoCache;
import com.todoapp.common.NotFoundException;
import com.todoapp.domain.Priority;
import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;
import com.todoapp.domain.User;
import com.todoapp.repository.TodoRepository;
import com.todoapp.todo.dto.TodoRequest;
import com.todoapp.todo.dto.TodoResponse;
import com.todoapp.todo.dto.TodoStatsResponse;

@Service
public class TodoService {

	private static final Logger log = LoggerFactory.getLogger(TodoService.class);

	/** Upper bound so a wide-open query cannot pull an unbounded result set. */
	private static final int MAX_RESULTS = 500;

	private final TodoRepository todoRepository;
	private final TodoCache todoCache;

	public TodoService(TodoRepository todoRepository, TodoCache todoCache) {
		this.todoRepository = todoRepository;
		this.todoCache = todoCache;
	}

	/**
	 * Returns the cached default view when available.
	 * <p>
	 * Only the unfiltered, default-sorted request is cacheable. Any other
	 * combination is a distinct key we deliberately do not keep, because a write
	 * could not invalidate them all without SCAN.
	 */
	@Transactional(readOnly = true)
	public List<TodoResponse> listCached(Long userId, String search, TodoStatus status, Priority priority,
			String sort) {

		boolean cacheable = isDefaultView(search, status, priority, sort);

		if (cacheable) {
			Optional<List<TodoResponse>> cached = this.todoCache.getList(userId);
			if (cached.isPresent()) {
				log.debug("Cache HIT  list user={}", userId);
				return cached.get();
			}
			log.debug("Cache MISS list user={}", userId);
		}

		List<Todo> results = loadList(userId, search, status, priority, sort);
		List<TodoResponse> response = results.stream().map(TodoResponse::from).toList();

		if (cacheable) {
			this.todoCache.putList(userId, response);
		}
		return response;
	}

	/** The exact request shape whose result is stored under one predictable key. */
	private boolean isDefaultView(String search, TodoStatus status, Priority priority, String sort) {
		boolean noSearch = (search == null || search.isBlank());
		boolean noStatus = (status == null);
		boolean noPriority = (priority == null);
		boolean defaultSort = (sort == null || sort.isBlank() || "default".equalsIgnoreCase(sort));
		return noSearch && noStatus && noPriority && defaultSort;
	}

	private List<Todo> loadList(Long userId, String search, TodoStatus status, Priority priority, String sort) {
		Specification<Todo> spec = TodoSpecifications.ownedBy(userId);

		if (search != null && !search.isBlank()) {
			spec = spec.and(TodoSpecifications.textContains(search.trim()));
		}
		if (status != null) {
			spec = spec.and(TodoSpecifications.withStatus(status));
		}
		if (priority != null) {
			spec = spec.and(TodoSpecifications.withPriority(priority));
		}
		spec = spec.and(TodoSpecifications.ordered(sort));

		// Cap protects against an unbounded result set; the ordering travels in the
		// Specification, so this stays a plain (unpaged) query.
		return this.todoRepository.findAll(spec, PageRequest.of(0, MAX_RESULTS)).getContent();
	}

	@Transactional(readOnly = true)
	public Todo get(Long userId, Long id) {
		return findOwned(userId, id);
	}

	@Transactional
	public Todo create(User user, TodoRequest request) {
		Todo todo = new Todo(user, request.title().trim());
		applyFields(todo, request);
		Todo saved = this.todoRepository.save(todo);
		// Deferred to after commit - evicting here would let a concurrent reader
		// repopulate the cache from pre-commit data and keep it stale forever.
		this.todoCache.evictAfterCommit(user.getId());
		log.info("Created todo id={} for user={}", saved.getId(), user.getId());
		return saved;
	}

	@Transactional
	public Todo update(Long userId, Long id, TodoRequest request) {
		Todo todo = findOwned(userId, id);
		todo.setTitle(request.title().trim());
		applyFields(todo, request);
		this.todoCache.evictAfterCommit(userId);
		return todo;
	}

	@Transactional
	public Todo changeStatus(Long userId, Long id, TodoStatus status) {
		Todo todo = findOwned(userId, id);
		// Routed through the entity so completedAt stays consistent with status.
		todo.setStatus(status);
		// Status changes move rows between the DONE bucket, so stats are affected
		// as well as the list.
		this.todoCache.evictAfterCommit(userId);
		return todo;
	}

	@Transactional
	public void delete(Long userId, Long id) {
		Todo todo = findOwned(userId, id);
		this.todoRepository.delete(todo);
		this.todoCache.evictAfterCommit(userId);
		log.info("Deleted todo id={} for user={}", id, userId);
	}

	/**
	 * Five COUNT queries per call, so this was the single most expensive read in
	 * the app - and it is fetched on every single page load, alongside the list.
	 * Caching it removes most of the database traffic a screen view generates.
	 */
	@Transactional(readOnly = true)
	public TodoStatsResponse stats(Long userId) {
		Optional<TodoStatsResponse> cached = this.todoCache.getStats(userId);
		if (cached.isPresent()) {
			log.debug("Cache HIT  stats user={}", userId);
			return cached.get();
		}
		log.debug("Cache MISS stats user={}", userId);

		TodoStatsResponse computed = new TodoStatsResponse(
				this.todoRepository.countByUserId(userId),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.TODO),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.IN_PROGRESS),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.DONE),
				this.todoRepository.countOverdue(userId, Instant.now()));

		this.todoCache.putStats(userId, computed);
		return computed;
	}

	private void applyFields(Todo todo, TodoRequest request) {
		todo.setDescription(blankToNull(request.description()));

		if (request.priority() != null) {
			todo.setPriority(request.priority());
		}
		todo.setDueDate(request.dueDate());

		// Only touch status when the caller actually sent one, so a partial update
		// that omits status does not silently reset an in-progress item to TODO.
		if (request.status() != null) {
			todo.setStatus(request.status());
		}
	}

	/**
	 * Scoped by user on purpose: "not found" and "belongs to someone else" both
	 * surface as 404, so the API never confirms that someone else's id exists.
	 */
	private Todo findOwned(Long userId, Long id) {
		return this.todoRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("To-do " + id + " was not found"));
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}

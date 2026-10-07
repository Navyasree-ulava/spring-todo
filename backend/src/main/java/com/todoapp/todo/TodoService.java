package com.todoapp.todo;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todoapp.common.NotFoundException;
import com.todoapp.domain.Priority;
import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;
import com.todoapp.domain.User;
import com.todoapp.repository.TodoRepository;
import com.todoapp.todo.dto.TodoRequest;
import com.todoapp.todo.dto.TodoStatsResponse;

@Service
public class TodoService {

	private static final Logger log = LoggerFactory.getLogger(TodoService.class);

	/** Upper bound so a wide-open query cannot pull an unbounded result set. */
	private static final int MAX_RESULTS = 500;

	private final TodoRepository todoRepository;

	public TodoService(TodoRepository todoRepository) {
		this.todoRepository = todoRepository;
	}

	@Transactional(readOnly = true)
	public List<Todo> list(Long userId, String search, TodoStatus status, Priority priority, String sort) {
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
		log.info("Created todo id={} for user={}", saved.getId(), user.getId());
		return saved;
	}

	@Transactional
	public Todo update(Long userId, Long id, TodoRequest request) {
		Todo todo = findOwned(userId, id);
		todo.setTitle(request.title().trim());
		applyFields(todo, request);
		return todo;
	}

	@Transactional
	public Todo changeStatus(Long userId, Long id, TodoStatus status) {
		Todo todo = findOwned(userId, id);
		// Routed through the entity so completedAt stays consistent with status.
		todo.setStatus(status);
		return todo;
	}

	@Transactional
	public void delete(Long userId, Long id) {
		Todo todo = findOwned(userId, id);
		this.todoRepository.delete(todo);
		log.info("Deleted todo id={} for user={}", id, userId);
	}

	@Transactional(readOnly = true)
	public TodoStatsResponse stats(Long userId) {
		return new TodoStatsResponse(
				this.todoRepository.countByUserId(userId),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.TODO),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.IN_PROGRESS),
				this.todoRepository.countByUserIdAndStatus(userId, TodoStatus.DONE),
				this.todoRepository.countOverdue(userId, Instant.now()));
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

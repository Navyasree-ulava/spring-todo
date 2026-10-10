package com.todoapp.todo;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.todoapp.domain.Priority;
import com.todoapp.domain.TodoStatus;
import com.todoapp.repository.UserRepository;
import com.todoapp.common.NotFoundException;
import com.todoapp.domain.User;
import com.todoapp.security.TodoPrincipal;
import com.todoapp.todo.dto.TodoRequest;
import com.todoapp.todo.dto.TodoResponse;
import com.todoapp.todo.dto.TodoStatsResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private final TodoService todoService;
	private final UserRepository userRepository;

	public TodoController(TodoService todoService, UserRepository userRepository) {
		this.todoService = todoService;
		this.userRepository = userRepository;
	}

	@GetMapping
	public List<TodoResponse> list(
			@AuthenticationPrincipal TodoPrincipal principal,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TodoStatus status,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) String sort) {
		// Service decides whether this shape is cacheable; filtered views are not.
		return this.todoService.listCached(principal.getId(), search, status, priority, sort);
	}

	@GetMapping("/stats")
	public TodoStatsResponse stats(@AuthenticationPrincipal TodoPrincipal principal) {
		return this.todoService.stats(principal.getId());
	}

	@GetMapping("/{id}")
	public TodoResponse get(@AuthenticationPrincipal TodoPrincipal principal, @PathVariable Long id) {
		return TodoResponse.from(this.todoService.get(principal.getId(), id));
	}

	@PostMapping
	public ResponseEntity<TodoResponse> create(@AuthenticationPrincipal TodoPrincipal principal,
			@Valid @RequestBody TodoRequest request) {
		User user = this.userRepository.findById(principal.getId())
				.orElseThrow(() -> new NotFoundException("Account no longer exists"));

		TodoResponse created = TodoResponse.from(this.todoService.create(user, request));
		return ResponseEntity.created(URI.create("/api/todos/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public TodoResponse update(@AuthenticationPrincipal TodoPrincipal principal, @PathVariable Long id,
			@Valid @RequestBody TodoRequest request) {
		return TodoResponse.from(this.todoService.update(principal.getId(), id, request));
	}

	/** Separate from PUT so toggling the checkbox does not resend the whole object. */
	@PatchMapping("/{id}/status")
	public TodoResponse changeStatus(@AuthenticationPrincipal TodoPrincipal principal, @PathVariable Long id,
			@RequestParam TodoStatus status) {
		return TodoResponse.from(this.todoService.changeStatus(principal.getId(), id, status));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal TodoPrincipal principal, @PathVariable Long id) {
		this.todoService.delete(principal.getId(), id);
		return ResponseEntity.noContent().build();
	}

}

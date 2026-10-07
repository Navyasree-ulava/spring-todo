package com.todoapp.todo.dto;

import java.time.Instant;

import com.todoapp.domain.Priority;
import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;

/**
 * The client-facing view of a to-do. Deliberately excludes the owning User, so
 * the payload stays small and the entity is not exposed.
 */
public record TodoResponse(Long id, String title, String description, TodoStatus status, Priority priority,
		Instant dueDate, boolean overdue, Instant completedAt, Instant createdAt, Instant updatedAt) {

	public static TodoResponse from(Todo todo) {
		return new TodoResponse(
				todo.getId(),
				todo.getTitle(),
				todo.getDescription(),
				todo.getStatus(),
				todo.getPriority(),
				todo.getDueDate(),
				todo.isOverdue(),
				todo.getCompletedAt(),
				todo.getCreatedAt(),
				todo.getUpdatedAt());
	}

}

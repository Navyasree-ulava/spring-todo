package com.todoapp.todo.dto;

import java.time.Instant;

import com.todoapp.domain.Priority;
import com.todoapp.domain.TodoStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TodoRequest(

		@NotBlank(message = "Title is required")
		@Size(max = 255, message = "Title must be at most 255 characters")
		String title,

		@Size(max = 10_000, message = "Description must be at most 10000 characters")
		String description,

		TodoStatus status,

		Priority priority,

		Instant dueDate) {

}

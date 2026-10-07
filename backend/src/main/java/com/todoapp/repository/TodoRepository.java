package com.todoapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

	/**
	 * Always look up by (id, userId) rather than findById. A plain findById would
	 * happily return someone else's to-do, and the caller would have no way to
	 * tell the difference.
	 */
	Optional<Todo> findByIdAndUserId(Long id, Long userId);

	long countByUserId(Long userId);

	long countByUserIdAndStatus(Long userId, TodoStatus status);

}

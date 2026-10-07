package com.todoapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;

public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

	List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

	/**
	 * Always look up by (id, userId) rather than findById. A plain findById would
	 * happily return someone else's to-do, and the caller would have no way to
	 * tell the difference.
	 */
	Optional<Todo> findByIdAndUserId(Long id, Long userId);

	long countByUserId(Long userId);

	long countByUserIdAndStatus(Long userId, TodoStatus status);

	/** Written in JPQL so the comparison happens in the database, not in Java. */
	@Query("""
			select count(t) from Todo t
			where t.user.id = :userId
			  and t.dueDate is not null
			  and t.dueDate < :now
			  and t.status <> com.todoapp.domain.TodoStatus.DONE
			""")
	long countOverdue(@Param("userId") Long userId, @Param("now") Instant now);

}

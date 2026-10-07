package com.todoapp.todo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.todoapp.domain.Priority;
import com.todoapp.domain.Todo;
import com.todoapp.domain.TodoStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;

import org.springframework.data.domain.Sort;

/**
 * Reusable query fragments for filtering and ordering the to-do list.
 * <p>
 * Each returns a Specification that composes with {@code and(...)}, so the
 * controller applies exactly the filters the request asked for. Doing this in
 * Criteria rather than in Java keeps the work in the database.
 */
public final class TodoSpecifications {

	private TodoSpecifications() {
	}

	/** Scopes every query to one user. This is the guard that prevents data leaks. */
	public static Specification<Todo> ownedBy(Long userId) {
		return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
	}

	/** Case-insensitive contains-match across title and description. */
	public static Specification<Todo> textContains(String term) {
		String pattern = "%" + term.toLowerCase() + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("title")), pattern),
				cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern));
	}

	public static Specification<Todo> withStatus(TodoStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<Todo> withPriority(Priority priority) {
		return (root, query, cb) -> cb.equal(root.get("priority"), priority);
	}

	/**
	 * Applies ordering through the Criteria API instead of {@code JpaSort.unsafe}.
	 * <p>
	 * JpaSort passes the expression straight into ORDER BY, but Spring Data then
	 * tries to resolve the leading {@code t.} as a <em>property path</em> and
	 * fails with "No property 't' found". Criteria builds the correct alias for us.
	 * <p>
	 * Ordering also lives in a Specification rather than a Pageable Sort because a
	 * Specification can express a CASE expression, which we need for priority.
	 */
	public static Specification<Todo> ordered(String sort) {
		return (root, query, cb) -> {
			if (query != null) {
				query.orderBy(buildOrders(cb, root, sort));
			}
			return cb.conjunction();
		};
	}

	private static Order[] buildOrders(CriteriaBuilder cb, jakarta.persistence.criteria.Root<Todo> root, String sort) {
		List<Order> orders = new ArrayList<>();
		String key = (sort == null || sort.isBlank()) ? "created" : sort.toLowerCase();

		switch (key) {
			case "duedate" -> {
				orders.add(nullsLastFlag(cb, root.get("dueDate")));
				orders.add(cb.asc(root.get("dueDate")));
				orders.add(cb.desc(root.get("createdAt")));
			}
			case "duedatedesc" -> {
				orders.add(nullsLastFlag(cb, root.get("dueDate")));
				orders.add(cb.desc(root.get("dueDate")));
				orders.add(cb.desc(root.get("createdAt")));
			}
			case "priority" -> {
				orders.add(cb.asc(priorityRank(cb, root)));
				orders.add(cb.asc(root.get("createdAt")));
			}
			case "title" -> orders.add(cb.asc(root.get("title")));
			case "createdat" -> orders.add(cb.desc(root.get("createdAt")));
			// Default: most urgent first, newest within each band.
			default -> {
				orders.add(cb.asc(priorityRank(cb, root)));
				orders.add(cb.desc(root.get("createdAt")));
			}
		}
		return orders.toArray(new Order[0]);
	}

	/**
	 * Priority is persisted as a string enum, so a plain ASC sort orders it
	 * alphabetically - HIGH, LOW, MEDIUM. That is not what "sort by priority"
	 * means to anyone, so it needs a CASE that maps the enum onto 0/1/2.
	 */
	private static Expression<Integer> priorityRank(CriteriaBuilder cb,
			jakarta.persistence.criteria.Root<Todo> root) {
		return cb.<Integer>selectCase()
				.when(cb.equal(root.get("priority"), Priority.HIGH), cb.literal(0))
				.when(cb.equal(root.get("priority"), Priority.MEDIUM), cb.literal(1))
				.when(cb.equal(root.get("priority"), Priority.LOW), cb.literal(2))
				.otherwise(cb.literal(3));
	}

	/**
	 * JPA's Order has no nullsLast(), so undated items are pushed to the end with
	 * a CASE: nulls sort as 1, everything else as 0. The direction of the real
	 * value column is applied separately, so nulls stay last either way.
	 */
	private static Order nullsLastFlag(CriteriaBuilder cb, Path<?> path) {
		return cb.asc(cb.<Integer>selectCase()
				.when(cb.isNull(path), cb.literal(1))
				.otherwise(cb.literal(0)));
	}

}

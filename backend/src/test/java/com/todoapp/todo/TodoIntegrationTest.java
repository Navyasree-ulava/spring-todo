package com.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4 moved this out of the old autoconfigure package
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.todoapp.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TodoIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createReadUpdateDeleteRoundTrip() throws Exception {
		Cookie session = signUp("hank@test.com");

		String created = mockMvc.perform(post("/api/todos")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Write the README","description":"Explain the setup","priority":"HIGH"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("Write the README"))
				.andExpect(jsonPath("$.status").value("TODO"))
				.andExpect(jsonPath("$.overdue").value(false))
				.andReturn().getResponse().getContentAsString();

		long id = json(created).get("id").asLong();

		mockMvc.perform(get("/api/todos/" + id).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Write the README"));

		mockMvc.perform(patch("/api/todos/" + id + "/status").param("status", "DONE").cookie(session))
				.andExpect(status().isOk())
				// Completing must stamp completedAt - that invariant lives in Todo.setStatus
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.completedAt").isNotEmpty());

		// Un-completing must clear it again
		mockMvc.perform(patch("/api/todos/" + id + "/status").param("status", "TODO").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").isEmpty());

		mockMvc.perform(put("/api/todos/" + id)
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Write the README properly","priority":"LOW","status":"IN_PROGRESS"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Write the README properly"))
				.andExpect(jsonPath("$.priority").value("LOW"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		mockMvc.perform(delete("/api/todos/" + id).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todos/" + id).cookie(session))
				.andExpect(status().isNotFound());
	}

	@Test
	void oneUserCannotSeeOrModifyAnotherUsersTodo() throws Exception {
		Cookie alice = signUp("alice2@test.com");
		Cookie mallory = signUp("mallory@test.com");

		String created = mockMvc.perform(post("/api/todos")
				.cookie(alice)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Alice private task"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long id = json(created).get("id").asLong();

		// Must read as "does not exist" rather than "exists but forbidden" -
		// a 403 would confirm the id is real.
		mockMvc.perform(get("/api/todos/" + id).cookie(mallory))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/todos/" + id).cookie(mallory))
				.andExpect(status().isNotFound());

		// And it must not appear in Mallory's list or skew her counts
		mockMvc.perform(get("/api/todos").cookie(mallory))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/todos/stats").cookie(mallory))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void searchAndFilterNarrowTheList() throws Exception {
		Cookie session = signUp("ivy@test.com");

		createTodo(session, "Buy milk", "MEDIUM", "TODO");
		createTodo(session, "Write integration tests", "HIGH", "IN_PROGRESS");
		createTodo(session, "Ship release", "LOW", "TODO");

		mockMvc.perform(get("/api/todos").param("search", "integration").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Write integration tests"));

		// Search is a LIKE across title and description
		mockMvc.perform(get("/api/todos").param("search", "milk").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/todos").param("status", "IN_PROGRESS").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Write integration tests"));

		mockMvc.perform(get("/api/todos").param("priority", "HIGH").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void prioritySortOrdersByBusinessMeaningNotAlphabetically() throws Exception {
		Cookie session = signUp("jack@test.com");

		createTodo(session, "Low task", "LOW", "TODO");
		createTodo(session, "High task", "HIGH", "TODO");
		createTodo(session, "Medium task", "MEDIUM", "TODO");

		// The enum is stored as a string, so a plain ASC sort would give
		// HIGH, LOW, MEDIUM. This asserts we map it to HIGH, MEDIUM, LOW.
		mockMvc.perform(get("/api/todos").param("sort", "priority").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].priority").value("HIGH"))
				.andExpect(jsonPath("$[1].priority").value("MEDIUM"))
				.andExpect(jsonPath("$[2].priority").value("LOW"));
	}

	@Test
	void overdueTodoIsFlaggedWhileOpenButNotOnceCompleted() throws Exception {
		Cookie session = signUp("kate@test.com");

		// 2020-01-01 is unambiguously in the past
		String created = mockMvc.perform(post("/api/todos")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Overdue task","dueDate":"2020-01-01T00:00:00Z"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.overdue").value(true))
				.andReturn().getResponse().getContentAsString();

		long id = json(created).get("id").asLong();

		mockMvc.perform(get("/api/todos/stats").cookie(session))
				.andExpect(jsonPath("$.overdue").value(1));

		// Finishing the task should clear the overdue flag - a completed task
		// that is still "overdue" is a bug users notice immediately
		mockMvc.perform(patch("/api/todos/" + id + "/status").param("status", "DONE").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overdue").value(false));

		mockMvc.perform(get("/api/todos/stats").cookie(session))
				.andExpect(jsonPath("$.overdue").value(0));
	}

	@Test
	void futureDueDateIsNotOverdue() throws Exception {
		Cookie session = signUp("nina@test.com");

		mockMvc.perform(post("/api/todos")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Due next year","dueDate":"2099-01-01T00:00:00Z"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.overdue").value(false));
	}

	@Test
	void statsCountEachBucket() throws Exception {
		Cookie session = signUp("leo@test.com");

		String a = createTodo(session, "One", "MEDIUM", "TODO");
		String b = createTodo(session, "Two", "MEDIUM", "IN_PROGRESS");
		String c = createTodo(session, "Three", "MEDIUM", "TODO");
		mockMvc.perform(patch("/api/todos/" + json(c).get("id").asLong() + "/status")
				.param("status", "DONE").cookie(session)).andExpect(status().isOk());

		mockMvc.perform(get("/api/todos/stats").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.todo").value(1))
				.andExpect(jsonPath("$.inProgress").value(1))
				.andExpect(jsonPath("$.done").value(1))
				.andExpect(jsonPath("$.overdue").value(0));
	}

	@Test
	void todosRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/todos")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Anonymous"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void blankTitleIsRejected() throws Exception {
		Cookie session = signUp("mia@test.com");

		mockMvc.perform(post("/api/todos")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"   "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").exists());
	}

	// ---- helpers ----

	private Cookie signUp(String email) throws Exception {
		var result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("email", email, "password", "supersecret123", "displayName", "Test User"))))
				.andExpect(status().isOk())
				.andReturn();
		return result.getResponse().getCookie("todo_token");
	}

	private String createTodo(Cookie session, String title, String priority, String status) throws Exception {
		return mockMvc.perform(post("/api/todos")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("title", title, "priority", priority, "status", status))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private JsonNode json(String body) {
		try {
			return objectMapper.readTree(body);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not parse response: " + body, ex);
		}
	}

}

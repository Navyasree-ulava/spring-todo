package com.todoapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4 moved this out of the old autoconfigure package
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.todoapp.AbstractIntegrationTest;

// Boot 4 ships Jackson 3, which lives in the tools.jackson namespace.
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void registerThenUseSessionCookieOnProtectedEndpoint() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"alice@test.com","password":"supersecret123","displayName":"Alice"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("alice@test.com"))
				.andExpect(jsonPath("$.displayName").value("Alice"))
				// The hash must never leave the server
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		Cookie session = registerAndCaptureCookie("bob@test.com");

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("bob@test.com"));
	}

	@Test
	void protectedEndpointRejectsAnonymousCaller() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void loginWithCorrectPasswordSucceeds() throws Exception {
		Cookie session = registerAndCaptureCookie("carol@test.com");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"carol@test.com","password":"supersecret123"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("carol@test.com"));
	}

	@Test
	void loginWithWrongPasswordIsRejected() throws Exception {
		registerAndCaptureCookie("dave@test.com");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"dave@test.com","password":"totally-wrong"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateEmailIsRejectedWithConflict() throws Exception {
		registerAndCaptureCookie("erin@test.com");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"erin@test.com","password":"another-password","displayName":"Erin Two"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	void emailIsCaseInsensitiveForDuplicates() throws Exception {
		registerAndCaptureCookie("frank@test.com");

		// Same address, different casing, must still be treated as a duplicate
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"FRANK@test.com","password":"another-password","displayName":"Frank Two"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	void shortPasswordFailsValidation() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"grace@test.com","password":"short","displayName":"Grace"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void malformedJsonReturnsBadRequestNotServerError() throws Exception {
		// A 500 here would mean the API is leaking server faults to clients
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void logoutClearsTheSessionCookie() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isNoContent())
				.andExpect(result -> {
					Cookie cookie = result.getResponse().getCookie("todo_token");
					assertThat(cookie).isNotNull();
					assertThat(cookie.getMaxAge()).isZero();
				});
	}

	private Cookie registerAndCaptureCookie(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						java.util.Map.of("email", email, "password", "supersecret123",
								"displayName", "Test User"))))
				.andExpect(status().isOk())
				.andReturn();

		Cookie cookie = result.getResponse().getCookie("todo_token");
		assertThat(cookie).as("session cookie should be set on register").isNotNull();
		assertThat(cookie.isHttpOnly()).as("cookie must be httpOnly").isTrue();
		return cookie;
	}

}

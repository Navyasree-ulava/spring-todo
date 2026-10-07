package com.todoapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be a valid address")
		@Size(max = 255, message = "Email must be at most 255 characters")
		String email,

		// 72 is the hard ceiling: BCrypt silently ignores anything past 72 bytes,
		// so a longer password would give the user a false sense of strength.
		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
		String password,

		@NotBlank(message = "Display name is required")
		@Size(max = 80, message = "Display name must be at most 80 characters")
		String displayName) {

}

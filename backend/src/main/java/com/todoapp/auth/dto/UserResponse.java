package com.todoapp.auth.dto;

import com.todoapp.domain.AuthProvider;
import com.todoapp.domain.User;

/**
 * The user shape the frontend is allowed to see. Note what is missing:
 * passwordHash, googleId, emailVerified. Returning the entity directly would
 * leak the hash and couple the API to the table layout.
 */
public record UserResponse(Long id, String email, String displayName, String avatarUrl,
		boolean emailVerified, AuthProvider authProvider, boolean googleLinked) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getAvatarUrl(),
				user.isEmailVerified(),
				user.getAuthProvider(),
				user.isGoogleLinked());
	}

}

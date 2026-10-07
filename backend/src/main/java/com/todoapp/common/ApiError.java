package com.todoapp.common;

import java.time.Instant;
import java.util.Map;

/**
 * One consistent error shape for the whole API, so the frontend can render
 * failures without special-casing every endpoint.
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		Map<String, String> fieldErrors) {

	public static ApiError of(int status, String error, String message, String path) {
		return new ApiError(Instant.now(), status, error, message, path, null);
	}

	public static ApiError of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
		return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
	}

}

package com.todoapp.common;

/** Maps to HTTP 409 - the request was valid but clashes with existing state. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}

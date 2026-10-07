package com.todoapp.domain;

/**
 * How a user most recently authenticated. A single account can use both LOCAL
 * and GOOGLE - this records the primary/first one, not the only one.
 */
public enum AuthProvider {
	LOCAL,
	GOOGLE
}

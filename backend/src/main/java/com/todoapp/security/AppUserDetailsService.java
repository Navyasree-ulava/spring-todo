package com.todoapp.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.todoapp.repository.UserRepository;

/**
 * Bridges Spring Security's "load a user by username" lookup to our repository.
 * Spring calls this automatically during {@code AuthenticationManager} password
 * checks, so the DAO layer never has to know it exists.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public AppUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return this.userRepository.findByEmailIgnoreCase(email)
				.map(TodoPrincipal::from)
				// Deliberately vague: a precise "no such user" versus "wrong password"
				// difference tells an attacker which emails are registered.
				.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
	}

}

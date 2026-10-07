package com.todoapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todoapp.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	Optional<User> findByGoogleId(String googleId);

}

package com.keval.pods.users.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.keval.pods.users.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

  // Query users by email
  List<User> findByEmail(String email);

  // Query user by id
  Optional<User> findById(Integer id);

  // Delete user by id
  void deleteById(Integer id);

  // Delete all users
  void deleteAll();
}

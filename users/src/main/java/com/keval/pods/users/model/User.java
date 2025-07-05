package com.keval.pods.users.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity   // Marks as Persistent Object for JPA
@Data     // Adds getters and setters for attributes
// Manually set table name since User (default) is reserved by H2
@Table(name = "Users")
public class User {
  @Id
  private Integer id;
  private String name;
  private String email;
  private Boolean discount_availed = false;
}

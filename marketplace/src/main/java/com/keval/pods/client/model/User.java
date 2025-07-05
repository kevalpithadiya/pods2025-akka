package com.keval.pods.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
  public Integer id;
  public Boolean discount_availed;

  // Empty constructor required for Jackson deserialization
  public User() {}

  public User(Integer id, Boolean discount_availed) {
    this.id = id;
    this.discount_availed = discount_availed;
  }

  @Override
  public String toString() {
      return "User{" +
              "id=" + id +
              ", discount_availed=" + discount_availed +
              '}';
  }
}

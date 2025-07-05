package com.keval.pods.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keval.pods.client.model.User;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class UsersClient {
  Config config = ConfigFactory.load();
  String baseUrl = config.getString("marketplace-app.service-urls.users");
  HttpClient httpClient = HttpClient.newHttpClient();
  
  public Optional<User> getUserById(Integer user_id) {
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(baseUrl + "/users/" + user_id))
      .GET()
      .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      // If request successful, deserialize and return response
      if (response.statusCode() == 200) {
        ObjectMapper objectMapper = new ObjectMapper();
        User user = objectMapper.readValue(response.body(), User.class);
        return Optional.of(user);
      }
      else {
        System.out.println("getUserById(" + user_id + ") failed (Code " + response.statusCode() +")");
        return Optional.empty();
      }
    }
    catch (Exception e) {
      System.out.println("getUserById(" + user_id + ") failed: " + e);
      return Optional.empty();
    }
  }

  public Integer setUserDiscountAvailed(Integer user_id, Boolean discount_availed) {
    User user = new User(user_id, discount_availed);

    try {
      // Convert user payload to JSON
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(user);

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/users/" + user_id))
        .PUT(BodyPublishers.ofString(requestBody))
        .header("Content-Type", "application/json")
        .build();

      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

      return response.statusCode();
    }
    catch (Exception e) {
      System.out.println("setUserDiscountAvailed(" + user_id + ", " + discount_availed +") failed: " + e);
      return 400;
    }
  }
}

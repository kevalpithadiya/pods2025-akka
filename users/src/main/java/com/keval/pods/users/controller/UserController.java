package com.keval.pods.users.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.keval.pods.users.client.MarketplaceClient;
import com.keval.pods.users.client.WalletsClient;
import com.keval.pods.users.model.User;
import com.keval.pods.users.repository.UserRepository;

@RestController
public class UserController {
  // Obtain the user repository
  @Autowired
  private UserRepository userRepository;
  
  // Read marketplace and wallets service URLs from configuration files
  @Value("${pods.marketplaceUrl}")
  private String marketplaceUrl;
  @Value("${pods.walletsUrl}")
  private String walletsUrl;

  // Clients for the marketplace and wallets services
  private MarketplaceClient marketplaceClient = new MarketplaceClient();
  private WalletsClient walletsClient = new WalletsClient();

  /* Endpoint 0: GET /hello
   * Testing endpoint
   * Returns the configured marketplace and wallets service URLs
  */
  @GetMapping(path = "/hello")
  public String hello() {
    return String.format("Hello World! <br/><br/> Configuration: <br/> Marketplace URL: %s <br/> Wallets URL: %s", marketplaceUrl, walletsUrl);
  }

  /* Endpoint 1: POST /users
   * Request Body:
   *  {
   *    "name": "...",
   *    "email": "..."
   *  }
   */
  @PostMapping(path = "/users", consumes = "application/json")
  public ResponseEntity<User> createUser(@RequestBody User user) {
    // Verify required fields are present: id, name, email
    // Return 400 BAD REQUEST if not
    if (user.getId() == null || user.getName() == null || user.getEmail() == null) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Check if user with same id exists (not mentioned in the spec but needed)
    // Return 400 BAD REQUEST if so
    if(!userRepository.findById(user.getId()).isEmpty()) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Check if user with same email exists
    // Return 400 BAD REQUEST if so
    List<User> foundUsers = userRepository.findByEmail(user.getEmail());
    if (!foundUsers.isEmpty()) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Try to save the new user
    // Return 201 CREATED with saved user JSON if successful
    try {
      user.setDiscount_availed(false);
      User savedUser = userRepository.save(user);
      return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    // Return 500 INTERNAL SERVER ERROR if save fails
    catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /* Endpoint 2: GET /users/{userId} */
  @GetMapping(path = "/users/{userId}")
  public ResponseEntity<User> getUserById(@PathVariable Integer userId) {
    // Try to find user with userId
    // Return 200 OK with user JSON if found
    try {
      User user = userRepository.findById(userId).orElseThrow();
      return new ResponseEntity<>(user, HttpStatus.OK);
    }
    
    // Return 404 NOT FOUND if user is not found
    catch (NoSuchElementException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  /* Endpoint a: PUT /users/{userId}
   * Requesst Body:
   *  {
   *    "discount_availed": true/false
   *  }
   */
  @PutMapping(path = "/users/{userId}", consumes = "application/json")
  public ResponseEntity<User> updateUserById(@PathVariable Integer userId, @RequestBody User newUser) {
    // Check for discount_availed field in request body
    // Return 400 BAD REQUEST if not present
    if (newUser.getDiscount_availed() == null) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    
    // Try to find user with userId
    // Return 202 ACCEPTED with user JSON if user successfully updated
    try {
      User existingUser = userRepository.findById(userId).orElseThrow();
      existingUser.setDiscount_availed(newUser.getDiscount_availed());

      User savedUser = userRepository.save(existingUser);
      return new ResponseEntity<>(savedUser, HttpStatus.ACCEPTED);
    }
    // Return 404 NOT FOUND if user is not found
    catch (NoSuchElementException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    // Return 500 INTERNAL SERVER ERROR if save fails
    catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /* Endpoint 3: DELETE /users/{userId} */
  @DeleteMapping(path = "/users/{userId}")
  public ResponseEntity<User> deleteUserbyId(@PathVariable Integer userId) {
    // Try to find user with userId
    // Return 200 OK if deleted successfully
    try {
      @SuppressWarnings("unused")
      User user = userRepository.findById(userId).orElseThrow();

      // Cancel user's orders using marketplace service
      marketplaceClient.cancelAllOrdersByUserId(marketplaceUrl, userId);

      // Delete user's wallet using wallet service
      walletsClient.deleteWalletByUserId(walletsUrl, userId);

      // Delete user from JPA
      userRepository.deleteById(userId);
      return new ResponseEntity<>(HttpStatus.OK);
    }
    // Return 404 NOT FOUND if user is not found
    catch (NoSuchElementException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  /* Endpoint 4: DELETE /users */
  @DeleteMapping(path = "/users")
  public ResponseEntity<User> deleteAllUsers() {

    // REMOVED: Reset marketplace, i.e., cancel all orders
    // marketplaceClient.cancelAllOrders(marketplaceUrl);
    
    // Remove all wallets
    walletsClient.deleteAllWallets(walletsUrl);

    // Delete all users from JPA
    userRepository.deleteAll();
    return new ResponseEntity<>(HttpStatus.OK);
  }
}

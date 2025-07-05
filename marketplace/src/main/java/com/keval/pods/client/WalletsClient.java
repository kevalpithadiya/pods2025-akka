package com.keval.pods.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keval.pods.client.model.WalletTrxn;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class WalletsClient {
  Config config = ConfigFactory.load();
  String baseUrl = config.getString("marketplace-app.service-urls.wallets");
  HttpClient httpClient = HttpClient.newHttpClient();
  
  public Integer putWalletTrxn(Integer user_id, WalletTrxn walletTrxn) {
    try {
      // Convert walletTrxn to JSON
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(walletTrxn);

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/wallets/" + user_id))
        .PUT(BodyPublishers.ofString(requestBody))
        .header("Content-Type", "application/json")
        .build();

      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

      return response.statusCode();
    }
    catch (Exception e) {
      System.out.println("putWalletTrxn(" + user_id + ", " + walletTrxn +") failed: " + e);
      return 400;
    }
  }
}

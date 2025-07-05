package com.keval.pods.users.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
public class WalletsClient {
  /**
   * Delete user's wallet
   * <p> Sends DELETE request to "/wallets/{userId}" </p>
   * 
   * @return 200 if wallet deleted, 404 if wallet not found
   */
  public HttpStatus deleteWalletByUserId(String url, Integer userId) {
    RestClient restClient = RestClient.create();
    
    try {
      restClient.delete()
        .uri(url + "/wallets/{userId}", userId)
        .retrieve()
        .body(String.class);
      
      return HttpStatus.OK;
    }
    catch (HttpClientErrorException e) {
      return HttpStatus.NOT_FOUND;
    }
    catch (Exception e) {
      log.error(e, e);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
  }

  /**
   * Delete all wallets
   * <p> Sends DELETE request to "/wallets" </p>
   * 
   * @return 200 always
   */
  public HttpStatus deleteAllWallets(String url) {
    RestClient restClient = RestClient.create();
    
    try {
      restClient.delete()
        .uri(url + "/wallets")
        .retrieve()
        .body(String.class);
      
      return HttpStatus.OK;
    }
    catch (HttpClientErrorException e) {
      return HttpStatus.NOT_FOUND;
    }
    catch (Exception e) {
      log.error(e, e);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
  }
}

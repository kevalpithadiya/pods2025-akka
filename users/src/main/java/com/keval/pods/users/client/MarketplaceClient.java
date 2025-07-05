package com.keval.pods.users.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
public class MarketplaceClient {
  /**
   * Cancels all orders place by user
   * <p>Sends DELETE request to "/marketplace/users/{userId}"</p>
   * 
   * @return 200 if atleast one order was cancelled, 404 if no orders were cancelled
   */
  public HttpStatus cancelAllOrdersByUserId(String url, Integer userId) {
    RestClient restClient = RestClient.create();
    
    try {
      restClient.delete()
        .uri(url + "/marketplace/users/{userId}", userId)
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
   * REMOVED: Cancels all order on the marketplace
   * <p> Sends DELETE request to "/marketplace" </p>
   * 
   * @return 200 always
   */
  // public HttpStatus cancelAllOrders(String url) {
  //   RestClient restClient = RestClient.create();
      
  //   try {
  //     restClient.delete()
  //       .uri(url + "/marketplace")
  //       .retrieve()
  //       .body(String.class);
      
  //     return HttpStatus.OK;
  //   }
  //   catch (HttpClientErrorException e) {
  //     return HttpStatus.NOT_FOUND;
  //   }
  //   catch (Exception e) {
  //     log.error(e, e);
  //     return HttpStatus.INTERNAL_SERVER_ERROR;
  //   }
  // }
}

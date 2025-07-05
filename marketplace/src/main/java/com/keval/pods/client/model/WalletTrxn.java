package com.keval.pods.client.model;

public class WalletTrxn {
  public static String DEBIT = "debit";
  public static String CREDIT = "credit";

  public String action;
  public Integer amount;

  public WalletTrxn(String action, Integer amount) {
    this.action = action;
    this.amount = amount;
  }

  @Override
  public String toString() {
      return "WalletTrxn{" +
              "action=" + action +
              ", amount=" + amount +
              '}';
  }
}

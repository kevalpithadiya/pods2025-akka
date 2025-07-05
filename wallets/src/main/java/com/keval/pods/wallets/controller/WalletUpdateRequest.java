package com.keval.pods.wallets.controller;

import lombok.Data;

// Request Body: {"action": "debit" or "credit","amount": 500} for PUT /wallets/{userId}
@Data
public class WalletUpdateRequest {
    private String action;
    private Integer amount;
}

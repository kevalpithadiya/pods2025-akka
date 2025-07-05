package com.keval.pods.wallets.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.keval.pods.wallets.model.Wallet;
import com.keval.pods.wallets.repository.WalletRepository;

import jakarta.transaction.Transactional;

@RestController
public class WalletController {

    @Autowired
    private WalletRepository walletRepository;

    //GET /wallets/{userId}     
    //Get the balance of the user
    @GetMapping(path = "/wallets/{user_id}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Integer user_id) {
        Integer balance = walletRepository.getBalance(user_id);
        
        if (balance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user_id", user_id);
        response.put("balance", balance);
    
        return ResponseEntity.ok(response); //return status code 200
    }
    

    //POST /wallets/{userId}     
    //Create a new wallet with balance 0
    //Controller Not required
    @PostMapping(path = "/wallets/{user_id}")
    @Transactional
    public ResponseEntity<Void> createWallet(@PathVariable Integer user_id){
        if(walletRepository.findById(user_id).isPresent()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet already exists");
        }
        Wallet wallet = new Wallet();
        wallet.setUser_id(user_id);
        wallet.setBalance(0);
        walletRepository.save(wallet);
        return ResponseEntity.ok().build(); //return status code 200

    }

    //PUT /wallets/{userId}         
    //Request Body: {"action": "debit" or "credit","amount": 500}
    // Update the wallet of the user
    //return the updated balance and status code 200 
    //Explicitely mention -H "Content-Type: application/json" in the curl command
    @PutMapping(path = "/wallets/{user_id}", consumes = "application/json")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateWallet(
        @PathVariable Integer user_id,
        @RequestBody WalletUpdateRequest walletUpdateRequest){

        // if wallet does not exist, create a new wallet with balance 0
        if(walletRepository.findById(user_id).isEmpty()){

            Wallet wallet = new Wallet();
            wallet.setUser_id(user_id);
            wallet.setBalance(0);
            walletRepository.save(wallet);

            walletRepository.getBalance(user_id);
        }

        // if wallet exists, update the wallet
    
        //If action is credit, add the amount to the balance
        Integer amount = walletUpdateRequest.getAmount();
        if(walletUpdateRequest.getAction().equals("credit")){
            if (amount < 0){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
            }
            walletRepository.creditWallet(user_id, amount);
        }
        //If action is debit, subtract the amount from the balance
        else if(walletUpdateRequest.getAction().equals("debit")){
            if (amount < 0){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
            }
            Integer balance = walletRepository.getBalance(user_id);
            if (balance < amount){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
            }
            walletRepository.debitWallet(user_id, amount);
        }
        //If action is neither credit nor debit, throw an exception
        else{
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
        }

        Integer updatedBalance = walletRepository.getBalance(user_id);

        Map<String, Object> response = new HashMap<>();
        response.put("user_id", user_id);
        response.put("balance", updatedBalance);
        return ResponseEntity.ok(response); //return status code 200 and updated balance json
    }

    //DELETE /wallets/{user_id}      
    //Delete the wallet of the user
    @DeleteMapping(path = "/wallets/{user_id}")
    public ResponseEntity<Void> deleteWallet(@PathVariable Integer user_id){
        if (walletRepository.findById(user_id).isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }
        walletRepository.deleteWallet(user_id);
        return ResponseEntity.ok().build(); //return status code 200  
    }

    //DELETE /wallets              
    //Delete all wallets 
    @DeleteMapping(path = "/wallets")
    public ResponseEntity<Void> deleteAllWallets(){
        walletRepository.deleteAll();
        return ResponseEntity.ok().build(); //return status code 200
    }
    
}

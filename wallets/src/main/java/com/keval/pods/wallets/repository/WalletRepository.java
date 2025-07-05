package com.keval.pods.wallets.repository;

import com.keval.pods.wallets.model.Wallet;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {

    //GET /wallets/{userId}
    @Query("SELECT w.balance FROM Wallet w WHERE w.user_id = :user_id")
    Integer getBalance(Integer user_id);

    //POST /wallets/{userId} --will be done in controller, using save method           

    //PUT /wallets/{userId}             
    //Request Body: {"action": "debit" or "credit","amount": 500}
    @Modifying
    @Query("UPDATE Wallet SET balance = balance + :amount WHERE user_id =:user_id")
    void creditWallet(Integer user_id, Integer amount);

    @Modifying
    @Query("UPDATE Wallet SET balance = balance - :amount WHERE user_id =:user_id")
    void debitWallet(Integer user_id, Integer amount);

    //DELETE /wallets/{userId}
    @Modifying
    @Transactional
    @Query("DELETE FROM Wallet WHERE user_id =:user_id")
    void deleteWallet(Integer user_id);

    //DELETE /wallet //Delete all wallets
    void deleteAll();
   
}

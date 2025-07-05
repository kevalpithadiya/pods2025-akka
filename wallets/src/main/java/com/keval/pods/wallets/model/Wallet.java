package com.keval.pods.wallets.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity  // Make a table in the database
@Data // Lombok annotation to generate getters and setters
@Table(name = "wallet") // Manually set the table name
public class Wallet {
    @Id
    private Integer user_id; //User id is the primary key, also a foreign key
    private Integer balance;
}

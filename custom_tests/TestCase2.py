# Place two orders and then mark one as delivered.
# Then cancel all orders associated with the user and check that only the non-delivered order is cancelled.


import requests

userServiceURL = "http://localhost:8080"
marketplaceServiceURL = "http://localhost:8081"
walletServiceURL = "http://localhost:8082"

def main():

    delete_users()
    name = "John Doe"
    email = "johndoe@mail.com"
    userId = 101
    print(f"=> create_user({name}, {email})", end = " | ")
    response_create_user = create_user(userId,name, email)
    print(f"response: {response_create_user.json()}")
    
    new_user = response_create_user.json()
    user_id = new_user['id']

    print(f"=> get_wallet({user_id})", end=" | ")
    create_wallet(user_id)
    response_get_wallet = get_wallet(user_id)
    print(f"response: {response_get_wallet.json()}")
    
    wallet = response_get_wallet.json()
    old_balance = wallet['balance']
    action = "credit"
    amount = 1000000
    
    print(f"=> update_wallet({user_id}, {action}, {amount})", end=" | ")
    response_update_wallet = update_wallet(user_id, action, amount)
    print(f"response: {response_update_wallet.json()}")

    # place two orders
    order1 = place_order(userId, 104, 1)
    order2 = place_order(userId, 102, 1)

    order1_id = order1.json()['order_id']
    order2_id = order2.json()['order_id']

    # mark order 1 as delivered   
    print(f"Marking Order 1 as Delivered") 
    requests.put(marketplaceServiceURL + f"/orders/{order1_id}", json={"order_id": order1_id,"status":"DELIVERED"})

    # cancel all orders for the user
    response = requests.delete(marketplaceServiceURL + f"/orders/{order2_id}")

    # check if only order 2 is cancelled
    response = requests.get(marketplaceServiceURL + f"/orders/{order1_id}")
    response_json = response.json()
    print("Order 1 Status: ", response_json['status'])
    if not response_json['status'] == "DELIVERED":
        print("Test Failed at 1")
        return
    response = requests.get(marketplaceServiceURL + f"/orders/{order2_id}")
    response_json = response.json()
    print("Order 2 Status: ", response_json['status'])
    if not response_json['status'] == "CANCELLED":
        print("Test Failed at 2")
        return
    print("Test Passed")
    



    print("")
    
def create_user(userid,name, email):
    new_user = {"id":userid,"name": name, "email": email}
    response = requests.post(userServiceURL + "/users", json=new_user)
    return response

def delete_users():
    requests.delete(userServiceURL+f"/users") 

def create_wallet(user_id):
    requests.put(walletServiceURL+f"/wallets/{user_id}", json={"action":"credit", "amount":0})

def get_wallet(user_id):
    response = requests.get(walletServiceURL + f"/wallets/{user_id}")
    return response
   
def update_wallet(user_id, action, amount):
    response = requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action":action, "amount":amount})
    return response

def place_order(user_id, product_id, quantity):
    id = 101
    productId = 106
    new_order = {"user_id": id,"items": [{"product_id": productId, "quantity": 1}]}
    response = requests.post(marketplaceServiceURL + "/orders", json=new_order)
    print("Order Placed Response: ", response.json())
    return response

def test_first_order_discount():
    #before this test, user is created, not order has been placed
    id = 101
    productId = 104
    # query by id if discount is available
    response_user = requests.get(userServiceURL + f"/users/{id}")
    discount_availed = bool(response_user.json()['discount_availed'])
    response_wallet = requests.get(walletServiceURL + f"/wallets/{id}")
    balance_before = response_wallet.json()['balance']
    print("Discount Availed: ", discount_availed)
    new_order = {"user_id": id,"items": [{"product_id": productId, "quantity": 1}]}
    response = requests.post(marketplaceServiceURL + "/orders", json=new_order)
    print("Order Placed Response: ", response.json())
    if response.status_code != 201: # 201 is the status code for order placed
        print("Test Failed at 3")
        return
    response_user = requests.get(userServiceURL + f"/users/{id}")
    discount_availed = bool(response_user.json()['discount_availed'])
    print("Discount Availed: ", discount_availed)
    if not discount_availed:
        print("Test Failed at 4")
        return
    response_wallet = requests.get(walletServiceURL + f"/wallets/{id}")
    balance_after = response_wallet.json()['balance']
    # check the balance difference and product price
    response_product = requests.get(marketplaceServiceURL + f"/products/{productId}")
    price = response_product.json()['price']
    print("Price: ", price,"|", " Balance Before: ", balance_before,"|", " Balance After: ", balance_after)
    if not (balance_before - balance_after) < price:
        print("Test Failed at 5")
        return
    print("Test Passed")
    print("")
    

def test_second_order_no_discount():
    #before this test, user is created, one order has been placed
    id = 101
    productId = 102
    # query by id if discount is available
    print("Placing Second Order")
    response_user = requests.get(userServiceURL + f"/users/{id}")
    discount_availed = bool(response_user.json()['discount_availed'])
    response_wallet = requests.get(walletServiceURL + f"/wallets/{id}")
    balance_before = response_wallet.json()['balance']
    print("Discount Availed: ", discount_availed)
    new_order = {"user_id": id,"items": [{"product_id": productId, "quantity": 1}]}
    response = requests.post(marketplaceServiceURL + "/orders", json=new_order)
    print("Order Placed Response: ", response.json())
    if response.status_code != 201: # 201 is the status code for order placed
        print("Test Failed at 6")
        return
    response_user = requests.get(userServiceURL + f"/users/{id}")
    discount_availed = bool(response_user.json()['discount_availed'])
    print("Discount Availed: ", discount_availed)
    if not discount_availed:
        print("Test Failed at 7")
        return
    response_wallet = requests.get(walletServiceURL + f"/wallets/{id}")
    balance_after = response_wallet.json()['balance']
    # check the balance difference and product price
    response_product = requests.get(marketplaceServiceURL + f"/products/{productId}")
    price = response_product.json()['price']
    print("Price: ", price,"|", " Balance Before: ", balance_before,"|", " Balance After: ", balance_after)
    if (balance_before - balance_after) == price:
        print("Test Passed")
        return
    print("Test Failed at 8")

if __name__ == "__main__":
    main()  
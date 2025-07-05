import sys
import random
from threading import Thread
import requests

from user import post_user
from wallet import put_wallet, get_wallet, test_get_wallet
from marketplace import (
    post_order,
    get_product,
    get_order,
    delete_order,
    test_get_product_stock,
    test_post_order,
    test_get_order
)
from utils import check_response_status_code, print_fail_message, print_pass_message

MARKETPLACE_SERVICE_URL = "http://localhost:8081"
WALLET_SERVICE_URL = "http://localhost:8082"


def delete_order_thread(order_id):
    """
    Thread function to concurrently delete an order.
    Returns the response object.
    """
    return delete_order(order_id)


def main():
    try:
        # 1) Create user
        user_id = 3001
        initial_wallet_balance = 1000000 # Balance after credit above, before order

        resp = post_user(user_id, "Concurrent Cancel User", "cancel_user@test.com")
        if not check_response_status_code(resp, 201):
            return False

        # 2) Credit wallet
        resp = put_wallet(user_id, "credit", initial_wallet_balance)  # Give enough balance
        if not check_response_status_code(resp, 200):
            return False

        # 3) Get initial product stock
        product_id = 101
        resp = get_product(product_id)
        if not check_response_status_code(resp, 200):
            return False
        product_data = resp.json()
        init_product_stock = product_data['stock_quantity']
        initial_stock = product_data['stock_quantity']
        order_quantity = 1
        product_price = product_data['price']

        # 4) Place an order
        items_to_order = [{"product_id": product_id, "quantity": order_quantity}]
        resp = post_order(user_id, items_to_order)
        if not check_response_status_code(resp, 201):
            return False
        order_data = resp.json()
        order_id = order_data['order_id']
        order_total_price = order_data['total_price']

        # 5) Launch concurrent DELETE /orders/{orderId} requests
        threads = []
        responses = []
        num_threads = 2

        for _ in range(num_threads):
            t = Thread(target=lambda: responses.append(delete_order_thread(order_id)))
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        # 6) Verify results after concurrent cancellations

        # a) Check order status - should be CANCELLED
        resp = get_order(order_id)
        print(resp.json() , "with sts code", resp.status_code)
        if not test_get_order(order_id, resp, expected_status="CANCELLED") and not check_response_status_code(resp, 400):
            # print("Problem here")
            print_fail_message("Order status is not CANCELLED after concurrent deletions.")
            return False

        # b) Check product stock - should be restocked ONCE by the order_quantity
        expected_stock_increase = order_quantity

        expected_final_stock = init_product_stock
        resp = get_product(product_id)
        if not test_get_product_stock(product_id, resp, expected_stock=expected_final_stock):
            print_fail_message(f"Product stock not correctly restored. Expected increase of {expected_stock_increase}.")
            return False

        # c) Check user wallet - should be refunded ONCE by the order_total_price
        expected_refund = order_total_price
        expected_final_balance = initial_wallet_balance
        resp = get_wallet(user_id)
        if not test_get_wallet(user_id, resp, expected_final_balance):
            print_fail_message(f"User wallet not correctly refunded. Expected refund of {expected_refund}.")
            return False

        print_pass_message("Concurrent order cancellation test passed: Stock and wallet updated correctly once.")
        return True

    except Exception as e:
        print_fail_message(f"Test crashed: {e}")
        return False

if __name__ == "__main__":
    if main():
        sys.exit(0)
    else:
        sys.exit(1)
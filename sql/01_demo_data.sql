USE `btl_16`;

-- PBKDF2-HMAC-SHA256, 120000 iterations.
-- demo/demo123, alice/alice123, bob/bob123.
INSERT INTO users(
    username, display_name, email, phone,
    password_hash, password_salt, password_iterations, active)
VALUES
('demo', 'Demo User', 'demo@local.test', NULL,
 'FevJRgE0jQPjQfenE+IyCxJ0OHW8DJ9ZdqDchG0xBRc=',
 'ABEiM0RVZneImaq7zN3u/w==', 120000, TRUE),
('alice', 'Alice', 'alice@local.test', NULL,
 'rnFHfqPtPy/YFx5wz6Y4ecB8sT+TKHFbgpb9f35GkLE=',
 'ECEyQ1RldoeYqbrL3O3+Dw==', 120000, TRUE),
('bob', 'Bob', 'bob@local.test', NULL,
 'KMv3hemdzG6y9u+SVp6qB5Y8G8tSuidGF7ILQVt8NZc=',
 '/+7dzLuqmYh3ZlVEMyIRAA==', 120000, TRUE)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO products(created_by, code, name, description, active)
SELECT user_id, 'HEADSET', 'Tai nghe gaming',
       'Tai nghe khong day de demo realtime va anti-sniping.', TRUE
FROM users WHERE username = 'bob'
UNION ALL
SELECT user_id, 'PHONE', 'Dien thoai thong minh',
       'San pham demo cho nhieu client cung theo doi.', TRUE
FROM users WHERE username = 'bob'
UNION ALL
SELECT user_id, 'LAPTOP', 'Laptop gaming',
       'Phien dau gia dai hon de kiem thu room va reconnect.', TRUE
FROM users WHERE username = 'bob'
ON DUPLICATE KEY UPDATE
    created_by = VALUES(created_by),
    name = VALUES(name),
    description = VALUES(description),
    active = TRUE;

INSERT INTO auctions(
    host_user_id, product_id, start_price, min_bid_increment,
    current_price, current_winner_id,
    start_time, end_time, status, ended_at, version)
SELECT u.user_id, p.product_id, 1000000.00, 50000.00, 1000000.00, NULL,
       DATE_SUB(NOW(3), INTERVAL 5 SECOND),
       DATE_ADD(NOW(3), INTERVAL 90 SECOND),
       'OPEN', NULL, 0
FROM products p JOIN users u ON u.username = 'bob' WHERE p.code = 'HEADSET';

INSERT INTO auctions(
    host_user_id, product_id, start_price, min_bid_increment,
    current_price, current_winner_id,
    start_time, end_time, status, ended_at, version)
SELECT u.user_id, p.product_id, 5000000.00, 100000.00, 5000000.00, NULL,
       DATE_SUB(NOW(3), INTERVAL 5 SECOND),
       DATE_ADD(NOW(3), INTERVAL 300 SECOND),
       'OPEN', NULL, 0
FROM products p JOIN users u ON u.username = 'bob' WHERE p.code = 'PHONE';

INSERT INTO auctions(
    host_user_id, product_id, start_price, min_bid_increment,
    current_price, current_winner_id,
    start_time, end_time, status, ended_at, version)
SELECT u.user_id, p.product_id, 10000000.00, 200000.00, 10000000.00, NULL,
       DATE_SUB(NOW(3), INTERVAL 5 SECOND),
       DATE_ADD(NOW(3), INTERVAL 480 SECOND),
       'OPEN', NULL, 0
FROM products p JOIN users u ON u.username = 'bob' WHERE p.code = 'LAPTOP';

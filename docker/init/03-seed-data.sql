USE TestEcommerceApp;
GO

-- Insert test customers
INSERT INTO Customer (Name, Email, CreatedDate) VALUES
('John Doe', 'john@example.com', GETDATE()),
('Jane Smith', 'jane@example.com', GETDATE()),
('Bob Wilson', 'bob@example.com', GETDATE()),
('Alice Brown', 'alice@example.com', GETDATE()),
('Charlie Davis', 'charlie@example.com', GETDATE()),
('Diana Miller', 'diana@example.com', GETDATE()),
('Frank Garcia', 'frank@example.com', GETDATE()),
('Grace Lee', 'grace@example.com', GETDATE()),
('Henry Taylor', 'henry@example.com', GETDATE()),
('Ivy Anderson', 'ivy@example.com', GETDATE());

-- Insert customer profiles (always-together relationship with Customer)
INSERT INTO CustomerProfile (CustomerId, Address, PhoneNumber) VALUES
(1, '123 Main St, City A', '555-0101'),
(2, '456 Oak Ave, City B', '555-0102'),
(3, '789 Pine Rd, City C', '555-0103'),
(4, '321 Elm St, City D', '555-0104'),
(5, '654 Maple Dr, City E', '555-0105'),
(6, '987 Cedar Ln, City F', '555-0106'),
(7, '147 Birch Way, City G', '555-0107'),
(8, '258 Willow Ct, City H', '555-0108'),
(9, '369 Spruce Ave, City I', '555-0109'),
(10, '741 Aspen Blvd, City J', '555-0110');

-- Insert test products
INSERT INTO Product (Name, Price, Category) VALUES
('Laptop', 999.99, 'Electronics'),
('Mouse', 29.99, 'Electronics'),
('Keyboard', 79.99, 'Electronics'),
('Monitor', 299.99, 'Electronics'),
('Desk Chair', 199.99, 'Furniture'),
('Coffee Mug', 14.99, 'Kitchen'),
('Notebook', 9.99, 'Office'),
('Pen Set', 19.99, 'Office'),
('Water Bottle', 24.99, 'Sports'),
('Phone Case', 39.99, 'Electronics');

-- Insert test orders
INSERT INTO [Order] (CustomerId, OrderDate, TotalAmount) VALUES
(1, GETDATE(), 1099.98),
(2, GETDATE(), 329.98),
(3, GETDATE(), 44.98),
(4, GETDATE(), 199.99),
(5, GETDATE(), 109.97),
(1, DATEADD(DAY, -1, GETDATE()), 79.99),
(2, DATEADD(DAY, -2, GETDATE()), 299.99),
(6, GETDATE(), 64.97),
(7, GETDATE(), 999.99),
(8, GETDATE(), 149.97);

-- Insert order items
INSERT INTO OrderItem (OrderId, ProductId, Quantity, Price) VALUES
(1, 1, 1, 999.99), (1, 2, 1, 29.99), (1, 3, 1, 79.99),
(2, 4, 1, 299.99), (2, 2, 1, 29.99),
(3, 6, 1, 14.99), (3, 7, 3, 9.99),
(4, 5, 1, 199.99),
(5, 8, 1, 19.99), (5, 9, 1, 24.99), (5, 10, 1, 39.99), (5, 6, 3, 14.99),
(6, 3, 1, 79.99),
(7, 4, 1, 299.99),
(8, 6, 1, 14.99), (8, 7, 5, 9.99),
(9, 1, 1, 999.99),
(10, 2, 1, 29.99), (10, 3, 1, 79.99), (10, 10, 1, 39.99);
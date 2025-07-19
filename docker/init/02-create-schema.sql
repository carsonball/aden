USE TestEcommerceApp;
GO

CREATE TABLE Customer (
    Id int IDENTITY(1,1) PRIMARY KEY,
    Name nvarchar(100) NOT NULL,
    Email nvarchar(100),
    CreatedDate datetime NOT NULL
);

CREATE TABLE CustomerProfile (
    CustomerId int PRIMARY KEY,
    Address nvarchar(200),
    PhoneNumber nvarchar(20),
    CONSTRAINT FK_CustomerProfile_Customer FOREIGN KEY (CustomerId) 
        REFERENCES Customer(Id)
);

CREATE TABLE [Order] (
    Id int IDENTITY(1,1) PRIMARY KEY,
    CustomerId int NOT NULL,
    OrderDate datetime NOT NULL,
    TotalAmount decimal(10,2) NOT NULL,
    CONSTRAINT FK_Order_Customer FOREIGN KEY (CustomerId) 
        REFERENCES Customer(Id)
);

CREATE TABLE Product (
    Id int IDENTITY(1,1) PRIMARY KEY,
    Name nvarchar(100) NOT NULL,
    Price decimal(10,2) NOT NULL,
    Category nvarchar(50)
);

CREATE TABLE OrderItem (
    Id int IDENTITY(1,1) PRIMARY KEY,
    OrderId int NOT NULL,
    ProductId int NOT NULL,
    Quantity int NOT NULL,
    Price decimal(10,2) NOT NULL,
    CONSTRAINT FK_OrderItem_Order FOREIGN KEY (OrderId) 
        REFERENCES [Order](Id),
    CONSTRAINT FK_OrderItem_Product FOREIGN KEY (ProductId) 
        REFERENCES Product(Id)
);
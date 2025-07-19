-- Enable Query Store for performance analysis
CREATE DATABASE TestEcommerceApp;
GO

USE TestEcommerceApp;
GO

-- Enable Query Store with appropriate settings for development
ALTER DATABASE TestEcommerceApp SET QUERY_STORE = ON (
    OPERATION_MODE = READ_WRITE,
    CLEANUP_POLICY = (STALE_QUERY_THRESHOLD_DAYS = 7),
    DATA_FLUSH_INTERVAL_SECONDS = 60,
    INTERVAL_LENGTH_MINUTES = 1,
    MAX_STORAGE_SIZE_MB = 100
);
GO
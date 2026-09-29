package com.ecommerce.inventory.exception;

public final class InventoryExceptions {

    private InventoryExceptions() {
    }

    public static class StockNotFoundException extends RuntimeException {
        public StockNotFoundException(String message) {
            super(message);
        }
    }

    public static class ReservationNotFoundException extends RuntimeException {
        public ReservationNotFoundException(String message) {
            super(message);
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    public static class ReservationStateException extends RuntimeException {
        public ReservationStateException(String message) {
            super(message);
        }
    }

    public static class InvalidStockRequestException extends RuntimeException {
        public InvalidStockRequestException(String message) {
            super(message);
        }
    }
}

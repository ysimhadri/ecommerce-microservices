package com.ecommerce.order.exception;

import java.util.UUID;

public final class OrderExceptions {

    private OrderExceptions() {
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }

    public static class CartEmptyException extends RuntimeException {
        public CartEmptyException(String message) {
            super(message);
        }
    }

    public static class CartForbiddenException extends RuntimeException {
        public CartForbiddenException(String message) {
            super(message);
        }
    }

    public static class CartNotFoundException extends RuntimeException {
        public CartNotFoundException(String message) {
            super(message);
        }
    }

    public static class CartNotActiveException extends RuntimeException {
        public CartNotActiveException(String message) {
            super(message);
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String message) {
            super(message);
        }
    }

    /** Payment HTTP 5xx, timeout, connection failure, or an open circuit. */
    public static class PaymentUnavailableException extends RuntimeException {
        public PaymentUnavailableException(String message) {
            super(message);
        }

        public PaymentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    public static class OrderForbiddenException extends RuntimeException {
        public OrderForbiddenException(String message) {
            super(message);
        }
    }

    public static class RemoteCallException extends RuntimeException {
        public RemoteCallException(String message) {
            super(message);
        }
    }

    /** Placement created an order, compensated the completed steps, and marked it cancelled. */
    public static class CompensatedOrderException extends RuntimeException {
        private final UUID orderId;
        private final String code;

        public CompensatedOrderException(UUID orderId, String code, String message) {
            super(message);
            this.orderId = orderId;
            this.code = code;
        }

        public UUID getOrderId() {
            return orderId;
        }

        public String getCode() {
            return code;
        }
    }
}

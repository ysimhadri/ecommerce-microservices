package com.ecommerce.payment.exception;

public final class PaymentExceptions {

    private PaymentExceptions() {
    }

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String message) {
            super(message);
        }
    }

    public static class PaymentConflictException extends RuntimeException {
        public PaymentConflictException(String message) {
            super(message);
        }
    }

    public static class PaymentForbiddenException extends RuntimeException {
        public PaymentForbiddenException(String message) {
            super(message);
        }
    }

    public static class PaymentUnavailableException extends RuntimeException {
        public PaymentUnavailableException(String message) {
            super(message);
        }
    }

    public static class InvalidPaymentRequestException extends RuntimeException {
        public InvalidPaymentRequestException(String message) {
            super(message);
        }
    }
}

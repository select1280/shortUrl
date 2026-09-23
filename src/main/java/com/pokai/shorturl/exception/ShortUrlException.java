package com.pokai.shorturl.exception;

import org.springframework.http.HttpStatus;

/**
 * 服務層的商業邏輯例外，帶著要回給前端的 HTTP 狀態碼。
 */
public class ShortUrlException extends RuntimeException {

    private final HttpStatus status;

    public ShortUrlException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ShortUrlException notFound(String shortCode) {
        return new ShortUrlException(HttpStatus.NOT_FOUND, "找不到短碼: " + shortCode);
    }

    public static ShortUrlException gone(String shortCode) {
        return new ShortUrlException(HttpStatus.GONE, "短碼已過期: " + shortCode);
    }

    public static ShortUrlException aliasTaken(String alias) {
        return new ShortUrlException(HttpStatus.CONFLICT, "自訂短碼已被使用: " + alias);
    }

    public static ShortUrlException badRequest(String message) {
        return new ShortUrlException(HttpStatus.BAD_REQUEST, message);
    }
}

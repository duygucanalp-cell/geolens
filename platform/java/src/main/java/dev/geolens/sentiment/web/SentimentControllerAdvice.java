package dev.geolens.sentiment.web;

import dev.geolens.common.ApiError;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sentiment controller'ı için hata çeviricisi — Go handler'ının davranış parity'si.
 * <p>Go, istek gövdesi çözülemezse 400 + {@code {"error":"geçersiz istek"}} döner.
 * Spring JSON çözümlemesi controller öncesi yapıldığından burada yakalanır.
 */
@RestControllerAdvice(assignableTypes = SentimentController.class)
public class SentimentControllerAdvice {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("geçersiz istek"));
    }
}
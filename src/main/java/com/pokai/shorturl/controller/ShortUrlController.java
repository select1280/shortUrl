package com.pokai.shorturl.controller;

import com.pokai.shorturl.dto.CreateShortUrlRequest;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.service.ShortUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;

    /** 建立短網址 */
    @PostMapping("/api/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortUrlResponse create(@Valid @RequestBody CreateShortUrlRequest request) {
        return service.create(request);
    }

    /** 查詢短碼資訊（不算點擊） */
    @GetMapping("/api/urls/{shortCode}")
    public ShortUrlResponse info(@PathVariable String shortCode) {
        return service.getInfo(shortCode);
    }

    /**
     * 短網址導向。
     * 用 302 而非 301：301 會被瀏覽器永久快取，之後的點擊就不會回到服務端，點擊統計會失真。
     */
    @GetMapping("/{shortCode:[0-9A-Za-z]{4,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = service.resolveAndCount(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}

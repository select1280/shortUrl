package com.pokai.shorturl.controller;

import com.pokai.shorturl.click.ClickEvent;
import com.pokai.shorturl.click.ClickRecorder;
import com.pokai.shorturl.dto.CreateShortUrlRequest;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;
    private final ClickRecorder clickRecorder;
    private final Clock clock;

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
     * 只有導向成功才記錄點擊；短碼不存在或過期時 resolveAndCount 會丟例外，不會走到記錄這一步。
     */
    @GetMapping("/{shortCode:[0-9A-Za-z]{4,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String originalUrl = service.resolveAndCount(shortCode);
        clickRecorder.record(ClickEvent.from(request, shortCode, LocalDateTime.now(clock)));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}

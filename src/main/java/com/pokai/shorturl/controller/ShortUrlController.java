package com.pokai.shorturl.controller;

import com.pokai.shorturl.click.ClickEvent;
import com.pokai.shorturl.click.ClickRecorder;
import com.pokai.shorturl.dto.CreateShortUrlRequest;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "短網址", description = "建立短網址與導向")
public class ShortUrlController {

    private final ShortUrlService service;
    private final ClickRecorder clickRecorder;
    private final Clock clock;

    @PostMapping("/api/urls")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "建立短網址",
            description = "未指定 customAlias 時，短碼由資料庫自增 id 以 Base62 編碼產生")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "originalUrl 或 customAlias 格式不符", content = @Content),
            @ApiResponse(responseCode = "409", description = "自訂短碼已被使用", content = @Content)
    })
    public ShortUrlResponse create(@Valid @RequestBody CreateShortUrlRequest request) {
        return service.create(request);
    }

    @GetMapping("/api/urls/{shortCode}")
    @Operation(summary = "查詢短碼資訊", description = "不會累加點擊數")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功"),
            @ApiResponse(responseCode = "404", description = "短碼不存在", content = @Content)
    })
    public ShortUrlResponse info(@PathVariable String shortCode) {
        return service.getInfo(shortCode);
    }

    /**
     * 短網址導向。
     * 用 302 而非 301：301 會被瀏覽器永久快取，之後的點擊就不會回到服務端，點擊統計會失真。
     * 只有導向成功才記錄點擊；短碼不存在或過期時 resolveAndCount 會丟例外，不會走到記錄這一步。
     */
    @GetMapping("/{shortCode:[0-9A-Za-z]{4,16}}")
    @Operation(summary = "導向原始網址",
            description = "回 302 並把 Location 指向原始網址。用 302 而非 301，"
                    + "是因為 301 會被瀏覽器永久快取，之後的點擊不會再回到服務端，統計會失真。")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "導向原始網址", content = @Content),
            @ApiResponse(responseCode = "404", description = "短碼不存在", content = @Content),
            @ApiResponse(responseCode = "410", description = "短碼已過期", content = @Content)
    })
    public ResponseEntity<Void> redirect(
            @Parameter(description = "短碼，4~16 碼英數字", example = "000001") @PathVariable String shortCode,
            @Parameter(hidden = true) HttpServletRequest request) {
        String originalUrl = service.resolveAndCount(shortCode);
        clickRecorder.record(ClickEvent.from(request, shortCode, LocalDateTime.now(clock)));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}

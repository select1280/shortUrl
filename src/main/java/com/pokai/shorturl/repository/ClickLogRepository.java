package com.pokai.shorturl.repository;

import com.pokai.shorturl.dto.DailyClicks;
import com.pokai.shorturl.dto.TopShortUrl;
import com.pokai.shorturl.entity.ClickLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 時間條件一律寫成半開區間 clickedAt >= :from AND clickedAt < :to，不寫 DATE(clicked_at) BETWEEN ...：
 * 欄位被函式包住後，索引裡的 clicked_at 就用不上。
 * 實測 EXPLAIN：半開區間是 range（short_code + clicked_at 兩欄都用到），
 * 改寫成 DATE() 則退化成 ref（只用到 short_code，該短碼的每一筆點擊都要逐筆過濾）。
 */
public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {

    /** 單一短碼在期間內每天的點擊數。沒有點擊的日子不會出現在結果裡，由 service 補 0 */
    @Query("""
            select new com.pokai.shorturl.dto.DailyClicks(cast(c.clickedAt as LocalDate), count(c))
            from ClickLog c
            where c.shortCode = :shortCode
              and c.clickedAt >= :from and c.clickedAt < :to
            group by cast(c.clickedAt as LocalDate)
            order by cast(c.clickedAt as LocalDate)
            """)
    List<DailyClicks> countDailyClicks(@Param("shortCode") String shortCode,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * 期間內點擊數最多的短碼。
     * 用 Hibernate 6 的 entity join（on 條件自訂）把原始網址一起帶出來，前端不用再逐筆查。
     */
    @Query("""
            select new com.pokai.shorturl.dto.TopShortUrl(c.shortCode, u.originalUrl, count(c))
            from ClickLog c
            join UrlMapping u on u.shortCode = c.shortCode
            where c.clickedAt >= :from and c.clickedAt < :to
            group by c.shortCode, u.originalUrl
            order by count(c) desc, c.shortCode
            """)
    List<TopShortUrl> findTopShortUrls(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       Pageable pageable);
}

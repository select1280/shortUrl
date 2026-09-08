package com.pokai.shorturl.repository;

import com.pokai.shorturl.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * 直接在 DB 端累加，避免「讀出來 +1 再寫回」在併發下漏算。
     */
    @Modifying
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1 where u.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);
}

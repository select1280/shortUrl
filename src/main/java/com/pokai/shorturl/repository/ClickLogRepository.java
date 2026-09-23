package com.pokai.shorturl.repository;

import com.pokai.shorturl.entity.ClickLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {
}

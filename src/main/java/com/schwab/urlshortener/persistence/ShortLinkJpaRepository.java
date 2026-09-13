package com.schwab.urlshortener.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkJpaRepository extends JpaRepository<ShortLinkEntity, Long> {
    Optional<ShortLinkEntity> findByCode(String code);
}

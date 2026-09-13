package com.schwab.urlshortener.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RedirectEventJpaRepository extends JpaRepository<RedirectEventEntity, Long> {
}

package com.schwab.urlshortener.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ShortLinkRepositoryTest {

    @Autowired
    private ShortLinkJpaRepository repository;

    @Test
    void persistsAndReadsBackByCode() {
        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setCode("abc1234");
        entity.setLongUrl("https://example.com");
        repository.save(entity);

        ShortLinkEntity fetched = repository.findByCode("abc1234").orElseThrow();
        assertThat(fetched.getLongUrl()).isEqualTo("https://example.com");
    }
}

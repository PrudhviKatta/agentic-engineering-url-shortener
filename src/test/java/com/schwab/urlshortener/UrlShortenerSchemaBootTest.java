package com.schwab.urlshortener;

import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class UrlShortenerSchemaBootTest {

    @Autowired
    private ShortLinkJpaRepository repository;

    @Test
    void repositoryIsUsableAgainstTheRealConfiguredDatasourceAfterBoot() {
        assertThatCode(() -> repository.count()).doesNotThrowAnyException();
    }
}

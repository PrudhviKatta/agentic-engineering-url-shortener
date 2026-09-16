package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.CreateLinkRequest;
import com.schwab.urlshortener.persistence.RedirectEventJpaRepository;
import com.schwab.urlshortener.persistence.ShortLinkEntity;
import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LinkControllerPersistenceFailureTest {

    @Test
    void createLinkFailsClosedWhenPersistenceIsUnavailable() {
        ShortLinkJpaRepository links = mock(ShortLinkJpaRepository.class);
        RedirectEventJpaRepository redirects = mock(RedirectEventJpaRepository.class);
        when(links.save(any(ShortLinkEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("simulated persistence outage"));

        LinkController controller = new LinkController(links, redirects);

        assertThatThrownBy(() -> controller.createLink(
                new CreateLinkRequest("https://example.com", null)))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("simulated persistence outage");
    }
}

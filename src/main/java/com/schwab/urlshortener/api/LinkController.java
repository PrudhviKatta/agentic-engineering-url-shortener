package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.AnalyticsResponse;
import com.schwab.urlshortener.api.dto.CreateLinkRequest;
import com.schwab.urlshortener.api.dto.LinkResponse;
import com.schwab.urlshortener.domain.ShortCodeGenerator;
import com.schwab.urlshortener.domain.ShortLink;
import com.schwab.urlshortener.domain.UrlValidator;
import com.schwab.urlshortener.persistence.RedirectEventEntity;
import com.schwab.urlshortener.persistence.RedirectEventJpaRepository;
import com.schwab.urlshortener.persistence.ShortLinkEntity;
import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;

@RestController
public class LinkController {

    private final ShortLinkJpaRepository repository;
    private final RedirectEventJpaRepository redirectEventRepository;

    public LinkController(ShortLinkJpaRepository repository, RedirectEventJpaRepository redirectEventRepository) {
        this.repository = repository;
        this.redirectEventRepository = redirectEventRepository;
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LinkResponse createLink(@RequestBody CreateLinkRequest request) {
        String normalized = UrlValidator.validate(request.longUrl());

        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setLongUrl(normalized);
        entity.setExpiresAt(request.expiresAt());
        entity = repository.save(entity); // IDENTITY strategy: id is populated after this save

        String code = ShortCodeGenerator.generateCode(entity.getId());
        entity.setCode(code);
        repository.save(entity);

        return new LinkResponse(code, normalized, "/r/" + code, request.expiresAt());
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLinkEntity entity = repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "short_code_not_found"));
        ShortLink link = ShortLink.fromEntity(entity);

        if (link.isExpired()) {
            recordRedirect(entity, "expired");
            throw new ResponseStatusException(HttpStatus.GONE, "short_link_expired");
        }
        recordRedirect(entity, "redirected");
        return ResponseEntity.status(307).location(URI.create(link.longUrl())).build();
    }

    @GetMapping("/links/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        ShortLinkEntity entity = repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "short_code_not_found"));
        return new AnalyticsResponse(entity.getCode(), entity.getRedirectCount(), entity.getLastAccessedAt());
    }

    private void recordRedirect(ShortLinkEntity entity, String outcome) {
        RedirectEventEntity event = new RedirectEventEntity();
        event.setShortLinkId(entity.getId());
        event.setOutcome(outcome);
        redirectEventRepository.save(event);
        if ("redirected".equals(outcome)) {
            entity.setRedirectCount(entity.getRedirectCount() + 1);
            entity.setLastAccessedAt(Instant.now());
            repository.save(entity);
        }
    }
}

package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.CreateLinkRequest;
import com.schwab.urlshortener.api.dto.LinkResponse;
import com.schwab.urlshortener.domain.ShortCodeGenerator;
import com.schwab.urlshortener.domain.UrlValidator;
import com.schwab.urlshortener.persistence.ShortLinkEntity;
import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class LinkController {

    private final ShortLinkJpaRepository repository;

    public LinkController(ShortLinkJpaRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
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
}

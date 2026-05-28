package com.nkia.itg.common.exception.helper;

import com.nkia.itg.common.exception.ITGException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__test/exceptions")
public class ExceptionEmittingController {

    @PostMapping("/itg")
    public void throwItg() {
        throw new ITGException("META_NOT_FOUND", "메타를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }

    @PostMapping("/illegal-state")
    public void throwIllegalState() {
        throw new IllegalStateException("DRAFT only");
    }

    @PostMapping("/illegal-argument")
    public void throwIllegalArgument() {
        throw new IllegalArgumentException("invalid argument");
    }

    @PostMapping("/data-integrity")
    public void throwDataIntegrity() {
        throw new DataIntegrityViolationException("dup");
    }

    @PostMapping("/unknown")
    public void throwUnknown() {
        throw new RuntimeException("boom — should not leak to client");
    }

    @PostMapping("/validation")
    public void validateBody(@Valid @RequestBody Payload payload) {
        // no-op — body validation triggers MethodArgumentNotValidException
    }

    public record Payload(@NotBlank String name) {}
}

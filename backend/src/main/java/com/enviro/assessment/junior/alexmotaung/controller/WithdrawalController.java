package com.enviro.assessment.junior.alexmotaung.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import com.enviro.assessment.junior.alexmotaung.dto.*;
import com.enviro.assessment.junior.alexmotaung.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/investors/{investorId}/withdrawals")
@PreAuthorize("@portfolioAccess.owns(authentication, #investorId)")
public class WithdrawalController {
    private final WithdrawalService withdrawals;

    public WithdrawalController(WithdrawalService withdrawals) {
        this.withdrawals = withdrawals;
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> create(@PathVariable Long investorId,
                                                      @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(withdrawals.create(investorId, request));
    }

    @GetMapping
    public List<WithdrawalResponse> history(@PathVariable Long investorId) {
        return withdrawals.history(investorId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long investorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        var csv = withdrawals.export(investorId, from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"withdrawals-" + investorId + ".csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}

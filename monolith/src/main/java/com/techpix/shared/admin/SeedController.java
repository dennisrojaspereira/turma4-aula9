package com.techpix.shared.admin;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/seed")
public class SeedController {

    private final SeedService seed;

    public SeedController(SeedService seed) {
        this.seed = seed;
    }

    @PostMapping
    public SeedService.SeedSummary seed(@RequestParam(defaultValue = "2000") int accounts,
                                        @RequestParam(defaultValue = "200000") int payments,
                                        @RequestParam(defaultValue = "2000") int blacklist) {
        return seed.seed(accounts, payments, blacklist);
    }

    @GetMapping("/accounts")
    public List<UUID> accounts(@RequestParam(defaultValue = "2000") int limit) {
        return seed.seedAccountIds(limit);
    }
}

package com.techpix.fraud.internal;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.strangler.FraudModeConfig;
import com.techpix.fraud.internal.strangler.ShadowComparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos do módulo Fraud. Permitem trocar perfil e modo ao vivo e ler o
 * relatório do Parallel Run, sem reiniciar o processo. Em um sistema real, protegido por autenticação.
 */
@RestController
@RequestMapping("/admin/fraud")
public class FraudAdminController {

    public record ProfileRequest(FraudProfile profile) {
    }

    public record ModeRequest(FraudMode mode) {
    }

    private final FraudProfileConfig profileConfig;
    private final FraudModeConfig modeConfig;
    private final FraudService fraudService;
    private final ShadowComparator comparator;

    public FraudAdminController(FraudProfileConfig profileConfig, FraudModeConfig modeConfig,
                                FraudService fraudService, ShadowComparator comparator) {
        this.profileConfig = profileConfig;
        this.modeConfig = modeConfig;
        this.fraudService = fraudService;
        this.comparator = comparator;
    }

    @GetMapping("/profile")
    public Map<String, Object> currentProfile() {
        List<String> rules = fraudService.activeRuleNames();
        return Map.of("profile", profileConfig.active(), "ruleCount", rules.size(), "rules", rules);
    }

    @PutMapping("/profile")
    public Map<String, Object> changeProfile(@RequestBody ProfileRequest request) {
        profileConfig.activate(request.profile());
        return currentProfile();
    }

    @GetMapping("/mode")
    public Map<String, Object> currentMode() {
        return Map.of("mode", modeConfig.mode());
    }

    @PutMapping("/mode")
    public Map<String, Object> changeMode(@RequestBody ModeRequest request) {
        modeConfig.change(request.mode());
        return currentMode();
    }

    @GetMapping("/parallel-run")
    public Map<String, Object> parallelRun() {
        return comparator.summary();
    }

    @DeleteMapping("/parallel-run")
    public Map<String, Object> resetParallelRun() {
        comparator.reset();
        return comparator.summary();
    }
}

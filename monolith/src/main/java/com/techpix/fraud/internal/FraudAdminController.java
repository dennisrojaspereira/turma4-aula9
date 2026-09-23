package com.techpix.fraud.internal;

import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudProfileConfig;
import com.techpix.fraud.internal.FraudService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos do laboratório. Permitem trocar o perfil de Fraud ao vivo,
 * sem reiniciar o processo, para comparar "antes" e "depois" na mesma sessão.
 * Em um sistema real isso estaria protegido por autenticação.
 */
@RestController
@RequestMapping("/admin/fraud")
public class FraudAdminController {

    public record ProfileRequest(FraudProfile profile) {
    }

    private final FraudProfileConfig profileConfig;
    private final FraudService fraudService;

    public FraudAdminController(FraudProfileConfig profileConfig, FraudService fraudService) {
        this.profileConfig = profileConfig;
        this.fraudService = fraudService;
    }

    @GetMapping("/profile")
    public Map<String, Object> current() {
        List<String> rules = fraudService.activeRuleNames();
        return Map.of("profile", profileConfig.active(), "ruleCount", rules.size(), "rules", rules);
    }

    @PutMapping("/profile")
    public Map<String, Object> change(@RequestBody ProfileRequest request) {
        profileConfig.activate(request.profile());
        return current();
    }
}

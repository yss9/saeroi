package com.ssafy.upcycle.user.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.company-role-approval-guard.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CompanyRoleApprovalWebConfig implements WebMvcConfigurer {

    private final CompanyRoleApprovalInterceptor companyRoleApprovalInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(companyRoleApprovalInterceptor).addPathPatterns("/**");
    }
}

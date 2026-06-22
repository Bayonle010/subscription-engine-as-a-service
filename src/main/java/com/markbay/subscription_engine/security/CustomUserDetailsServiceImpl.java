package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.repository.MerchantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsServiceImpl implements UserDetailsService {

    private final MerchantUserRepository merchantUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Merchant user not found"));

        MerchantPrincipal principal = new MerchantPrincipal(merchantUser);

        if (!principal.isEnabled()) {
            throw new DisabledException("Merchant user is disabled");
        }

        return principal;
    }
}
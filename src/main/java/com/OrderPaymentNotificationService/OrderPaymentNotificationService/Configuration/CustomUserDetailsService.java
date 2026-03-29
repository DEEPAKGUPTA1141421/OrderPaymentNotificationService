package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // User user = userRepository.findByUsername(username)
        // .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Return a Spring Security User object
        return org.springframework.security.core.userdetails.User
                .withUsername("testuser") // Replace with user.getUsername()
                .password("password") // Replace with user.getPassword()
                .roles("USER") // Replace with user.getRoles()
                .build();
    }
}

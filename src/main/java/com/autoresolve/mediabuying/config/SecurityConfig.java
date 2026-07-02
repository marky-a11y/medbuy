package com.autoresolve.mediabuying.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers("/api/metrics/**").hasAnyRole("ADMIN", "MEDIA_ANALYST", "VIEWER")
                .antMatchers("/api/opportunity/**").hasAnyRole("ADMIN", "MEDIA_ANALYST", "VIEWER")
                .antMatchers("/api/export/**").hasAnyRole("ADMIN", "MEDIA_ANALYST")
                .antMatchers("/api/calculator/**").hasAnyRole("ADMIN", "MEDIA_ANALYST", "VIEWER")
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/", "/login", "/error", "/css/**", "/js/**", "/images/**",
                             "/actuator/health", "/actuator/info",
                             "/pipeline-debug", "/pipeline-debug/**",
                             "/calculator", "/calculator/**").permitAll()
                .antMatchers("/h2-console/**").denyAll()
                .anyRequest().authenticated()
            .and()
                .formLogin()
                .loginPage("/login").permitAll()
                .defaultSuccessUrl("/dashboard.xhtml", true)
            .and()
                .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            .and()
                .sessionManagement()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false);
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        manager.createUser(User.withUsername("analyst")
                .password(passwordEncoder().encode("analyst123"))
                .roles("MEDIA_ANALYST")
                .build());

        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

package de.marsg.wishlist.configs;

import de.marsg.wishlist.configs.security.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final String allowedOrigin;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
            @Value("${wishlist.cors.allowed-origin}") String allowedOrigin) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

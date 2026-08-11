package de.marsg.wishlist.configs.security;

import de.marsg.wishlist.endpoints.services.UserProvisioningService;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserEntity;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link AuthenticatedUser} controller parameters, provisioning the user on first contact. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ADMIN_AUTHORITY = "ROLE_admin";
    private static final String IDENTITY_PROVIDER_CLAIM = "identity_provider";

    private final UserProvisioningService provisioningService;

    public CurrentUserArgumentResolver(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        UserEntity user = provision(jwtAuth);
        boolean isAdmin = jwtAuth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
        // set by the realm's "identity_provider" session-note mapper on federated logins
        String identityProvider = jwtAuth.getToken().getClaimAsString(IDENTITY_PROVIDER_CLAIM);
        return new AuthenticatedUser(user.getId(), user.getKeycloakId(), user.getDisplayname(),
                user.getTier(), isAdmin, identityProvider);
    }

    /**
     * The first two requests of a new account can race into the same insert. The loser's
     * transaction rolls back, so the retry runs in a fresh one and simply finds the row
     * the winner committed — retrying inside the failed transaction could not work.
     */
    private UserEntity provision(JwtAuthenticationToken jwtAuth) {
        try {
            return provisioningService.provision(jwtAuth.getToken());
        } catch (DataIntegrityViolationException e) {
            return provisioningService.provision(jwtAuth.getToken());
        }
    }
}

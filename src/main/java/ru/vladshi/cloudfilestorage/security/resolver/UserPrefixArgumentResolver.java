package ru.vladshi.cloudfilestorage.security.resolver;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import ru.vladshi.cloudfilestorage.security.annotation.UserPrefix;
import ru.vladshi.cloudfilestorage.security.model.CustomUserDetails;
import ru.vladshi.cloudfilestorage.service.UserPrefixService;

@Component
@RequiredArgsConstructor
public class UserPrefixArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserPrefixService userPrefixService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(UserPrefix.class) != null &&
                parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(@Nonnull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @Nonnull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("Authentication is missing or invalid");
        }
        return userPrefixService.generate(userDetails.getId(), userDetails.getUsername());
    }
}

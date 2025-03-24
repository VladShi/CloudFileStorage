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
import ru.vladshi.cloudfilestorage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.security.model.CustomUserDetails;
import ru.vladshi.cloudfilestorage.service.UserPrefixService;

import java.util.Objects;

/**
 * Резолвер аргументов для аннотации {@link FullPath}.
 * <p>
 * Предоставляет полный путь в хранилище файлов, объединяя префикс пользователя (из данных аутентификации)
 * и дополнительный путь из параметра запроса, указанного в {@code pathParam} аннотации {@link FullPath}.
 * Префикс пользователя формируется с использованием {@code PathPrefixService}, а полный путь строится
 * путем добавления значения параметра запроса (если он присутствует). Результат всегда заканчивается символом "/".
 * <p>
 * Используется для автоматического предоставления полного пути (например, {@code "userId-username/path/"}) в методы
 * контроллера, помеченные аннотацией {@link FullPath}. Если аутентификация отсутствует или некорректна,
 * выбрасывается исключение {@code IllegalStateException}.
 *
 * @see FullPath
 * @see UserPrefixService
 */
@Component
@RequiredArgsConstructor
public class FullPathArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserPrefixService userPrefixService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(FullPath.class) != null &&
                parameter.getParameterType().equals(FullItemPath.class);
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
        String userPrefix = userPrefixService.buildUserPrefix(userDetails.getId(), userDetails.getUsername());

        FullPath annotation = parameter.getParameterAnnotation(FullPath.class);
        String pathParamName = Objects.requireNonNull(annotation).pathParam();
        String path = webRequest.getParameter(pathParamName);

        return new FullItemPath(userPrefix, path);
    }
}

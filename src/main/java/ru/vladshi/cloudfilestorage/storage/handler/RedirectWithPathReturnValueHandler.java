package ru.vladshi.cloudfilestorage.storage.handler;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import ru.vladshi.cloudfilestorage.storage.annotation.RedirectWithPath;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Slf4j
public class RedirectWithPathReturnValueHandler implements HandlerMethodReturnValueHandler {

    /**
     * Обработчик для аннотации {@link RedirectWithPath}.
     * Перехватывает методы контроллера с этой аннотацией и выполняет редирект
     * на основе параметра запроса, указанного в {@code pathParam}.
     */
    public RedirectWithPathReturnValueHandler() {
        log.info("RedirectWithPathReturnValueHandler initialized");
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        boolean supported = returnType.hasMethodAnnotation(RedirectWithPath.class) &&
                returnType.getParameterType().equals(void.class);
        log.debug("Supports return type: {}", supported);
        return supported;
    }

    @Override
    public void handleReturnValue(Object returnValue,
                                  @Nonnull MethodParameter returnType,
                                  @Nonnull ModelAndViewContainer mavContainer,
                                  @Nonnull NativeWebRequest webRequest) throws Exception {
        RedirectWithPath annotation = returnType.getMethodAnnotation(RedirectWithPath.class);
        String pathParamName = annotation.pathParam();

        String path = webRequest.getParameter(pathParamName);

        String redirectUrl = buildRedirectUrl(path);
        log.debug("Handling redirect to: {}", redirectUrl);
        mavContainer.setViewName(redirectUrl);
    }

    private String buildRedirectUrl(String path) {
        if (path != null && !path.isBlank()) {
            return "redirect:/?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
        }
        return "redirect:/";
    }
}

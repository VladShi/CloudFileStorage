package ru.vladshi.cloudfilestorage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import ru.vladshi.cloudfilestorage.handler.RedirectWithPathReturnValueHandler;
import ru.vladshi.cloudfilestorage.security.resolver.FullPathArgumentResolver;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final FullPathArgumentResolver fullPathArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(fullPathArgumentResolver);
    }

    @Bean
    public static BeanPostProcessor requestMappingHandlerAdapterPostProcessor(
            RedirectWithPathReturnValueHandler redirectWithPathReturnValueHandler) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RequestMappingHandlerAdapter adapter) {
                    List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(adapter.getReturnValueHandlers());
                    handlers.addFirst(redirectWithPathReturnValueHandler);
                    adapter.setReturnValueHandlers(handlers);
                    log.info("Added RedirectWithPathReturnValueHandler to RequestMappingHandlerAdapter via post-processor");
                }
                return bean;
            }
        };
    }
}

package ru.vladshi.cloudfilestorage.annotation;

import java.lang.annotation.*;

/**
 * Аннотация для методов контроллера, которая указывает, что после выполнения метода
 * должен быть выполнен редирект на корневой путь ("/") с опциональным параметром запроса,
 * указанным в {@code pathParam}. Используется для упрощения обработки операций,
 * где результатом является перенаправление пользователя обратно на главную страницу
 * с сохранением текущего пути в хранилище.
 * <p>
 * Аннотация обрабатывается кастомным {@code RedirectWithPathReturnValueHandler},
 * который извлекает значение параметра запроса, заданного в {@code pathParam},
 * и формирует URL редиректа вида {@code "redirect:/?path={encodedPath}"}.
 * Если параметр отсутствует или пустой, выполняется редирект на {@code "redirect:/"}.
 * <p>
 * Поддерживает методы с возвращаемым типом {@code void}, что позволяет избежать
 * явного возврата строки редиректа в коде контроллера.
 *
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @PostMapping("/create-folder")
 * @RedirectWithPath
 * public void createFolder(@FullPath FullItemPath path,
 *                          @RequestParam String newFolderName,
 *                          RedirectAttributes redirectAttributes) throws Exception {
 *     try {
 *         minioService.createFolder(path.full(), newFolderName);
 *     } catch (Exception e) {
 *         redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
 *     }
 * }
 * }</pre>
 * В этом примере после выполнения метода будет выполнен редирект на {@code "/?path={currentPath}"},
 * где {@code currentPath} извлекается из параметра запроса {@code "path"}.
 *
 * @see ru.vladshi.cloudfilestorage.handler.RedirectWithPathReturnValueHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedirectWithPath {
    /**
     * Имя параметра запроса, содержащего путь, который будет включен в URL редиректа.
     * По умолчанию используется значение {@code "path"}.
     * <p>
     * Например, если {@code pathParam = "folder"}, то редирект будет сформирован
     * на основе значения параметра {@code folder} из запроса.
     *
     * @return имя параметра запроса
     */
    String pathParam() default "path";
}
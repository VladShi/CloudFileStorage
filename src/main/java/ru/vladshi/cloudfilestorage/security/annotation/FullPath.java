package ru.vladshi.cloudfilestorage.security.annotation;

import ru.vladshi.cloudfilestorage.security.resolver.FullPathArgumentResolver;
import ru.vladshi.cloudfilestorage.service.UserPrefixService;

import java.lang.annotation.*;

/**
 * Аннотация для параметров методов контроллера, которая предоставляет полный путь в хранилище файлов,
 * объединяя префикс пользователя (из данных аутентификации) и опциональный путь из параметра запроса.
 * Полный путь формируется как {@code "userId-username/path/"}, где {@code path} извлекается
 * из параметра запроса, указанного в {@code pathParam}.
 * <p>
 * Используется для упрощения работы с путями в хранилище, объединяя базовый префикс пользователя
 * (генерируемый через {@code PathPrefixService}) и дополнительный путь из запроса в одну строку.
 * Аннотация обрабатывается кастомным {@code FullPathArgumentResolver}, который извлекает
 * данные аутентификации из {@code SecurityContextHolder} и параметр запроса из {@code NativeWebRequest}.
 * <p>
 * Применяется в методах контроллера, где требуется префикс пользователя или полный путь для операций с файлами,
 * таких как создание папок или загрузка файлов.
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
 * В этом примере, если текущий пользователь имеет ID 1 и имя "john_doe", а запрос содержит
 * параметр {@code path="documents"}, то {@code path.full()} будет равен {@code "1-john_doe/documents/"}.
 * Если параметр {@code path} отсутствует, результат будет {@code "1-john_doe/"}.
 *
 * @see FullPathArgumentResolver
 * @see UserPrefixService
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FullPath {
    String pathParam() default "path";
}

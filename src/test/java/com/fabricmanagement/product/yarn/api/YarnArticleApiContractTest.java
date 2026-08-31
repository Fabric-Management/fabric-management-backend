package com.fabricmanagement.product.yarn.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.api.controller.YarnArticleController;
import com.fabricmanagement.product.yarn.app.EndUseCatalogService;
import com.fabricmanagement.product.yarn.app.SpinningSystemCatalogService;
import com.fabricmanagement.product.yarn.app.TestMethodCatalogService;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class YarnArticleApiContractTest {

  private static final String READ = "@auth.can(authentication, 'yarn', 'read')";
  private static final String WRITE = "@auth.can(authentication, 'yarn', 'write')";
  private static final Pattern INVARIANT_LITERAL =
      Pattern.compile("\\bI(?:[1-9]|[12]\\d|3[01])\\b");

  @Test
  void everyMappedHandlerHasTheExactDerivedReadOrWriteGuard() {
    List<Method> handlers =
        Arrays.stream(YarnArticleController.class.getDeclaredMethods())
            .filter(YarnArticleApiContractTest::isMapped)
            .toList();

    assertThat(handlers).isNotEmpty();
    assertThat(handlers)
        .allSatisfy(
            method -> {
              PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
              assertThat(guard).as(method.getName()).isNotNull();
              assertThat(guard.value())
                  .as(method.getName())
                  .isEqualTo(method.isAnnotationPresent(GetMapping.class) ? READ : WRITE);
            });
  }

  @Test
  void apiLayerDoesNotDuplicateInvariantCatalogueLiterals() throws IOException {
    Path api = Path.of("src/main/java/com/fabricmanagement/product/yarn/api");
    try (var files = Files.walk(api)) {
      List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
      assertThat(javaFiles).isNotEmpty();
      for (Path file : javaFiles) {
        assertThat(Files.readString(file))
            .as(file.toString())
            .doesNotContainPattern(INVARIANT_LITERAL);
      }
    }
  }

  @Test
  void controllerDependsOnlyOnTheFourApplicationServicesItDelegatesTo() {
    assertThat(
            Arrays.stream(YarnArticleController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList())
        .containsExactlyInAnyOrder(
            YarnArticleService.class.getName(),
            SpinningSystemCatalogService.class.getName(),
            EndUseCatalogService.class.getName(),
            TestMethodCatalogService.class.getName());
  }

  private static boolean isMapped(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || method.isAnnotationPresent(PatchMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class);
  }
}

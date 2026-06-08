package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.fabricmanagement",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class OpenApiArchTest {

  @ArchTest
  static final ArchRule controllers_should_be_tagged =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(Tag.class)
          .because(
              "All controllers must have an OpenAPI @Tag for proper documentation grouping in frontend clients.");
}

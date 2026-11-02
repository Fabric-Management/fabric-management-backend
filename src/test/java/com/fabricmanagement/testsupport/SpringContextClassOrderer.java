package com.fabricmanagement.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Orders sibling test classes so that those sharing a Spring application context run one after
 * another, and can report which classes share which context.
 *
 * <p>Spring caches test contexts by their merged configuration; spring.properties keeps at most
 * four, because each holds database connections. Maven's Surefire and Failsafe hand JUnit one top
 * level class at a time, so this orderer only ever sees the {@code @Nested} classes of one test
 * class together. The report is what spans the whole run.
 *
 * <p>The key is the {@code MergedContextConfiguration} Spring itself builds for a class, so two
 * classes share a group exactly when they would share a cached context. Groups keep the order of
 * their first class and classes keep their order within a group; a class whose configuration cannot
 * be built keeps a group of its own. Registered in junit-platform.properties.
 *
 * <p>With {@code -Dspring.context.groups.report=target/context-groups} every class it sees is
 * recorded, and when the test JVM exits the groups, their configuration and their classes are
 * written to {@code target/context-groups-<classes>.txt}, one file per test JVM. Ordering happens
 * at discovery, so a run that filters every test out, such as {@code -Dgroups=none}, writes the
 * report in a few minutes.
 */
public class SpringContextClassOrderer implements ClassOrderer {

  /** The shared key of classes without a Spring context, so Spring is not asked about them. */
  private static final Object NO_SPRING_CONTEXT = new Object();

  private static final String REPORT_PROPERTY = "spring.context.groups.report";

  /** Every class seen in this JVM, by context key, in the order first seen. */
  private static final Map<Object, List<String>> SEEN = new LinkedHashMap<>();

  private static boolean reportScheduled;

  @Override
  public void orderClasses(ClassOrdererContext context) {
    List<? extends ClassDescriptor> classes = context.getClassDescriptors();
    Map<Object, Integer> groupOfKey = new HashMap<>();
    Map<ClassDescriptor, Integer> group = new IdentityHashMap<>();
    Map<ClassDescriptor, Integer> position = new IdentityHashMap<>();
    String report = System.getProperty(REPORT_PROPERTY);
    for (int i = 0; i < classes.size(); i++) {
      ClassDescriptor descriptor = classes.get(i);
      Object key = contextKey(descriptor.getTestClass());
      Integer existing = groupOfKey.get(key);
      int index = existing != null ? existing : groupOfKey.size();
      groupOfKey.putIfAbsent(key, index);
      group.put(descriptor, index);
      position.put(descriptor, i);
      if (report != null && !report.isBlank()) {
        record(report, key, descriptor.getTestClass().getName());
      }
    }
    classes.sort(
        Comparator.comparingInt((ClassDescriptor descriptor) -> group.get(descriptor))
            .thenComparingInt(position::get));
  }

  private static Object contextKey(Class<?> testClass) {
    // @SpringBootTest, @WebMvcTest, @JsonTest and the like carry @BootstrapWith;
    // @SpringJUnitConfig carries @ContextConfiguration. Enclosing classes count for @Nested ones.
    if (!TestContextAnnotationUtils.hasAnnotation(testClass, BootstrapWith.class)
        && !TestContextAnnotationUtils.hasAnnotation(testClass, ContextConfiguration.class)) {
      return NO_SPRING_CONTEXT;
    }
    try {
      return BootstrapUtils.resolveTestContextBootstrapper(testClass)
          .buildMergedContextConfiguration();
    } catch (RuntimeException | LinkageError e) {
      return testClass;
    }
  }

  private static synchronized void record(String report, Object key, String className) {
    SEEN.computeIfAbsent(key, k -> new ArrayList<>()).add(className);
    if (!reportScheduled) {
      reportScheduled = true;
      Runtime.getRuntime().addShutdownHook(new Thread(() -> writeReport(report)));
    }
  }

  private static synchronized void writeReport(String report) {
    int total = SEEN.values().stream().mapToInt(List::size).sum();
    long springGroups = SEEN.keySet().stream().filter(key -> key != NO_SPRING_CONTEXT).count();
    StringBuilder text = new StringBuilder();
    text.append(springGroups)
        .append(" Spring context configurations over ")
        .append(total)
        .append(" test classes (")
        .append(SEEN.getOrDefault(NO_SPRING_CONTEXT, List.of()).size())
        .append(" without a Spring context)\n");
    SEEN.entrySet().stream()
        .filter(entry -> entry.getKey() != NO_SPRING_CONTEXT)
        .sorted((a, b) -> b.getValue().size() - a.getValue().size())
        .forEach(
            entry -> {
              text.append("\n== ").append(entry.getValue().size()).append(" classes\n");
              text.append(entry.getKey()).append('\n');
              entry.getValue().forEach(name -> text.append("  ").append(name).append('\n'));
            });
    Path file = Path.of(report + "-" + total + ".txt");
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Files.writeString(file, text);
    } catch (IOException e) {
      System.err.println("Could not write " + file + ": " + e.getMessage());
    }
  }
}

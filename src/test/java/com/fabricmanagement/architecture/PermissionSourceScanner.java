package com.fabricmanagement.architecture;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.security.DataScopeGuard;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.springframework.security.core.Authentication;

/** Parses Java syntax without compiling or resolving types. Comments cannot become call sites. */
final class PermissionSourceScanner {
  enum Kind {
    ANNOTATION,
    JAVA
  }

  record Site(String file, long line, String resource, String action, Kind kind) {
    String key() {
      return resource + ":" + action;
    }

    String location() {
      return file + ":" + line;
    }
  }

  record Result(List<Site> sites, List<String> parallelVocabularies) {}

  record ApiDescriptor(
      Class<?> declaringType, String methodName, List<Class<?>> parameterTypes, int pairIndex) {
    ApiDescriptor {
      parameterTypes = List.copyOf(parameterTypes);
      if (pairIndex < 0
          || pairIndex + 1 >= parameterTypes.size()
          || parameterTypes.get(pairIndex) != String.class
          || parameterTypes.get(pairIndex + 1) != String.class) {
        throw new IllegalArgumentException(
            "Permission pair index must point to adjacent String parameters: "
                + declaringType.getSimpleName()
                + "."
                + methodName
                + parameterTypes);
      }
    }

    InvocationShape invocationShape() {
      return new InvocationShape(methodName, parameterTypes.size());
    }

    MethodSignature methodSignature() {
      return new MethodSignature(declaringType, methodName, parameterTypes);
    }
  }

  private record InvocationShape(String methodName, int arity) {}

  private record MethodSignature(
      Class<?> declaringType, String methodName, List<Class<?>> parameterTypes) {
    @Override
    public String toString() {
      return declaringType.getSimpleName() + "." + methodName + parameterTypes;
    }
  }

  // One table drives reflection signature checks and AST argument offsets.
  private static final List<ApiDescriptor> API_DESCRIPTORS =
      List.of(
          descriptor(DataScopeGuard.class, "currentScope", 0, String.class, String.class),
          descriptor(
              DataScopeGuard.class,
              "assertCanAccess",
              0,
              String.class,
              String.class,
              BaseEntity.class),
          descriptor(
              DataScopeGuard.class, "canAccess", 0, String.class, String.class, BaseEntity.class),
          descriptor(DataScopeGuard.class, "scopeFilter", 0, String.class, String.class),
          descriptor(PermissionResult.class, "can", 0, String.class, String.class),
          descriptor(PermissionResult.class, "scopeOf", 0, String.class, String.class),
          descriptor(
              SpELPermissionEvaluator.class,
              "can",
              1,
              Authentication.class,
              String.class,
              String.class),
          descriptor(
              SpELPermissionEvaluator.class,
              "hasScope",
              1,
              Authentication.class,
              String.class,
              String.class,
              String.class));
  private static final Pattern AUTH_CALL = Pattern.compile("@auth\\.(?:can|hasScope)\\s*\\(");
  private static final Pattern AUTH_LITERALS =
      Pattern.compile(
          "@auth\\.(?:can|hasScope)\\s*\\(\\s*authentication\\s*,\\s*(['\"])([^'\"]+)\\1\\s*,\\s*(['\"])([^'\"]+)\\3");

  private PermissionSourceScanner() {}

  static Result scanDirectory(Path root) throws IOException {
    Map<String, String> sources = new java.util.LinkedHashMap<>();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        sources.put(root.relativize(path).toString(), Files.readString(path));
      }
    }
    return scan(sources);
  }

  static Result scan(Map<String, String> sources) throws IOException {
    return scan(sources, API_DESCRIPTORS);
  }

  static Result scan(Map<String, String> sources, List<ApiDescriptor> descriptors)
      throws IOException {
    Map<InvocationShape, Integer> pairIndexes = pairIndexes(descriptors);
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("Permission scan requires a JDK");
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    List<JavaFileObject> files =
        sources.entrySet().stream()
            .<JavaFileObject>map(
                entry ->
                    new SimpleJavaFileObject(
                        URI.create("string:///" + entry.getKey().replace('\\', '/')),
                        JavaFileObject.Kind.SOURCE) {
                      @Override
                      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return entry.getValue();
                      }
                    })
            .toList();
    List<Site> sites = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    try (var fileManager =
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      JavacTask task =
          (JavacTask)
              compiler.getTask(null, fileManager, diagnostics, List.of("-proc:none"), null, files);
      Trees trees = Trees.instance(task);
      for (CompilationUnitTree unit : task.parse()) {
        new Visitor(unit, trees, sites, fields, pairIndexes).scan(unit, null);
      }
    }
    List<String> errors =
        diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(Object::toString)
            .toList();
    if (!errors.isEmpty())
      throw new IllegalStateException("Cannot parse permission sources: " + errors);
    return new Result(List.copyOf(sites), List.copyOf(fields));
  }

  /** Find APIs by signature, not parameter-name metadata (which javac may omit). */
  static Set<String> unscannedApis(Class<?>... types) {
    return unscannedApis(API_DESCRIPTORS, types);
  }

  static Set<String> unscannedApis(List<ApiDescriptor> descriptors, Class<?>... types) {
    pairIndexes(descriptors);
    Set<String> missing = new TreeSet<>();
    Set<Class<?>> inspectedTypes = Set.of(types);
    Set<MethodSignature> declared =
        descriptors.stream()
            .filter(descriptor -> inspectedTypes.contains(descriptor.declaringType()))
            .map(ApiDescriptor::methodSignature)
            .collect(java.util.stream.Collectors.toSet());
    Set<MethodSignature> actual = new java.util.HashSet<>();
    for (Class<?> type : types) {
      for (Method method : type.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) continue;
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i + 1 < parameters.length; i++) {
          if (parameters[i] == String.class && parameters[i + 1] == String.class) {
            actual.add(
                new MethodSignature(
                    type, method.getName(), List.copyOf(Arrays.asList(parameters))));
            break;
          }
        }
      }
    }
    actual.stream()
        .filter(signature -> !declared.contains(signature))
        .map(signature -> "Unscanned permission API " + signature)
        .forEach(missing::add);
    declared.stream()
        .filter(signature -> !actual.contains(signature))
        .map(signature -> "Stale permission API descriptor " + signature)
        .forEach(missing::add);
    return missing;
  }

  static List<ApiDescriptor> apiDescriptors() {
    return API_DESCRIPTORS;
  }

  static ApiDescriptor descriptor(
      Class<?> declaringType, String methodName, int pairIndex, Class<?>... parameterTypes) {
    return new ApiDescriptor(
        declaringType, methodName, List.copyOf(Arrays.asList(parameterTypes)), pairIndex);
  }

  private static Map<InvocationShape, Integer> pairIndexes(List<ApiDescriptor> descriptors) {
    Map<InvocationShape, Set<Integer>> indexesByShape = new java.util.LinkedHashMap<>();
    for (ApiDescriptor descriptor : descriptors) {
      indexesByShape
          .computeIfAbsent(descriptor.invocationShape(), ignored -> new TreeSet<>())
          .add(descriptor.pairIndex());
    }
    Map<InvocationShape, Integer> indexes = new java.util.LinkedHashMap<>();
    indexesByShape.forEach(
        (shape, pairIndexes) -> {
          if (pairIndexes.size() != 1) {
            throw new IllegalStateException(
                "Indistinguishable permission API overloads for "
                    + shape.methodName()
                    + "/"
                    + shape.arity()
                    + ": pair indexes "
                    + pairIndexes
                    + ". The parse-only scanner cannot resolve declaring types.");
          }
          indexes.put(shape, pairIndexes.iterator().next());
        });
    return Map.copyOf(indexes);
  }

  private static final class Visitor extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final Trees trees;
    private final List<Site> sites;
    private final List<String> fields;
    private final Map<InvocationShape, Integer> pairIndexes;

    Visitor(
        CompilationUnitTree unit,
        Trees trees,
        List<Site> sites,
        List<String> fields,
        Map<InvocationShape, Integer> pairIndexes) {
      this.unit = unit;
      this.trees = trees;
      this.sites = sites;
      this.fields = fields;
      this.pairIndexes = pairIndexes;
    }

    private long line(Tree tree) {
      return unit.getLineMap()
          .getLineNumber(trees.getSourcePositions().getStartPosition(unit, tree));
    }

    @Override
    public Void visitAnnotation(AnnotationTree tree, Void unused) {
      String name = tree.getAnnotationType().toString();
      if (name.endsWith("PreAuthorize") || name.endsWith("PostAuthorize")) {
        for (ExpressionTree argument : tree.getArguments()) {
          ExpressionTree value =
              argument instanceof AssignmentTree assignment ? assignment.getExpression() : argument;
          String expression = literal(value);
          if (expression == null) {
            throw new IllegalStateException(
                unit.getSourceFile().getName()
                    + ":"
                    + line(tree)
                    + " has an unscannable authorization annotation; use a literal expression");
          }
          Matcher calls = AUTH_CALL.matcher(expression);
          while (calls.find()) {
            Matcher pair =
                AUTH_LITERALS.matcher(expression).region(calls.start(), expression.length());
            if (!pair.lookingAt()) {
              throw new IllegalStateException(
                  unit.getSourceFile().getName()
                      + ":"
                      + line(tree)
                      + " has an unscannable @auth permission pair: "
                      + expression);
            }
            sites.add(
                new Site(
                    unit.getSourceFile().getName(),
                    line(tree),
                    pair.group(2),
                    pair.group(4),
                    Kind.ANNOTATION));
          }
        }
      }
      return super.visitAnnotation(tree, unused);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
      String name =
          switch (tree.getMethodSelect()) {
            case MemberSelectTree selected -> selected.getIdentifier().toString();
            case IdentifierTree identifier -> identifier.getName().toString();
            default -> "";
          };
      var arguments = tree.getArguments();
      Integer offset = pairIndexes.get(new InvocationShape(name, arguments.size()));
      if (offset != null) {
        if (arguments.size() >= offset + 2) {
          String resource = literal(arguments.get(offset));
          String action = literal(arguments.get(offset + 1));
          if (resource != null && action != null) {
            sites.add(
                new Site(unit.getSourceFile().getName(), line(tree), resource, action, Kind.JAVA));
          }
        }
      }
      return super.visitMethodInvocation(tree, unused);
    }

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
      if (!tree.getSimpleName().contentEquals("PermissionKey")) {
        for (Tree member : tree.getMembers()) {
          if (member instanceof VariableTree field && field.getType() != null) {
            String name = field.getName().toString().toUpperCase(Locale.ROOT);
            String type =
                field
                    .getType()
                    .toString()
                    .replace("java.util.", "")
                    .replace("java.lang.", "")
                    .replace(" ", "");
            if (type.equals("Set<String>")
                && (name.contains("RESOURCE") || name.contains("ACTION"))) {
              fields.add(
                  unit.getSourceFile().getName() + ":" + line(field) + " " + field.getName());
            }
          }
        }
      }
      return super.visitClass(tree, unused);
    }

    private static String literal(ExpressionTree expression) {
      if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value)
        return value;
      if (expression instanceof ParenthesizedTree parentheses)
        return literal(parentheses.getExpression());
      if (expression instanceof BinaryTree binary && binary.getKind() == Tree.Kind.PLUS) {
        String left = literal(binary.getLeftOperand());
        String right = literal(binary.getRightOperand());
        return left == null || right == null ? null : left + right;
      }
      return null;
    }
  }
}

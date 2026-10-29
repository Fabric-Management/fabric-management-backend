package com.fabricmanagement.common.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publishes every Jackson-discriminated union in one OpenAPI shape (OPENAPI-UNION-1).
 *
 * <p>A union root is a type that itself declares both {@link JsonTypeInfo} ({@code use = NAME},
 * {@code include = PROPERTY} or {@code EXISTING_PROPERTY}) and {@link JsonSubTypes}. Swagger Core
 * models such a root as inheritance: each member becomes {@code allOf: [$ref root, {properties}]}
 * while its {@code required} and {@code additionalProperties} stay outside the {@code allOf}, the
 * root lists the members in {@code oneOf} (a cycle) and the discriminator carries no mapping. A
 * closed member therefore rejects every valid payload, and clients cannot tell which type name
 * selects which member.
 *
 * <p>This converter publishes instead:
 *
 * <ul>
 *   <li>the root as {@code oneOf} of its members with {@code discriminator: {propertyName,
 *       mapping}}, and nothing else (description and title are kept);
 *   <li>each member as its own self-contained object whose discriminator property is a string
 *       pinned to the member's type names and listed in {@code required}. Its {@code
 *       additionalProperties} is whatever the member declares.
 * </ul>
 *
 * <p>{@link JsonSubTypes} is the only source of members and type names; the runtime reads the same
 * annotation, so the document and the reader cannot disagree. Declaring {@code oneOf}, {@code
 * allOf}, {@code anyOf}, {@code subTypes}, {@code discriminatorProperty} or {@code
 * discriminatorMapping} on the root's {@code @Schema} is refused as a second, drifting copy. A
 * sealed root must permit exactly the listed members.
 *
 * <p>The root is never handed to the rest of the chain, so Swagger's subtype resolution (the source
 * of the {@code allOf} back-reference) does not run for it. Members are resolved normally and
 * pinned after the chain returns, in every resolution context, whether they were reached through
 * the root or directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiscriminatedUnionModelConverter implements ModelConverter {

  private static final ClassValue<Optional<Union>> UNIONS =
      new ClassValue<>() {
        @Override
        protected Optional<Union> computeValue(Class<?> type) {
          return Optional.ofNullable(Union.declaredOn(type));
        }
      };

  private static final ClassValue<List<Union>> OWNERS =
      new ClassValue<>() {
        @Override
        protected List<Union> computeValue(Class<?> type) {
          return unionsListing(type);
        }
      };

  @Override
  @SuppressWarnings("rawtypes")
  public Schema resolve(
      AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    Class<?> raw = rawClass(type);
    Optional<Union> union = raw == null ? Optional.empty() : UNIONS.get(raw);
    if (union.isPresent()) {
      return resolveUnion(union.get(), type, context);
    }
    Schema<?> resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
    if (raw != null && resolved != null) {
      for (Union owner : OWNERS.get(raw)) {
        pinMember(owner, owner.member(raw), resolved, type, context);
      }
    }
    return resolved;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> resolveUnion(
      Union union, AnnotatedType type, ModelConverterContext context) {
    String rootName = decorated(type, nameOf(union.type()));
    Map<String, String> mapping = new LinkedHashMap<>();
    List<Schema> oneOf = new ArrayList<>();
    SpecVersion version = null;

    for (Member member : union.members()) {
      Schema<?> resolved =
          context.resolve(
              new AnnotatedType()
                  .type(member.type())
                  .jsonViewAnnotation(type.getJsonViewAnnotation())
                  .components(type.getComponents()));
      String memberName = componentName(resolved, decorated(type, nameOf(member.type())));
      Schema<?> model = context.getDefinedModels().get(memberName);
      if (model == null) {
        throw new IllegalStateException(
            "Union member "
                + member.type().getName()
                + " of "
                + union.type().getName()
                + " did not resolve to the named component "
                + memberName);
      }
      if (version == null) {
        version = model.getSpecVersion();
      }
      String ref = Components.COMPONENTS_SCHEMAS_REF + memberName;
      oneOf.add(reference(ref, model.getSpecVersion()));
      for (String typeName : member.names()) {
        mapping.put(typeName, ref);
      }
    }

    Schema root = new Schema<>();
    root.setSpecVersion(version);
    root.setName(rootName);
    io.swagger.v3.oas.annotations.media.Schema declared = union.schemaAnnotation();
    if (declared != null) {
      if (!declared.description().isBlank()) {
        root.setDescription(declared.description());
      }
      if (!declared.title().isBlank()) {
        root.setTitle(declared.title());
      }
    }
    root.setOneOf(oneOf);
    Discriminator discriminator = new Discriminator();
    discriminator.setPropertyName(union.property());
    discriminator.setMapping(mapping);
    root.setDiscriminator(discriminator);
    context.defineModel(rootName, root, type, null);
    return reference(Components.COMPONENTS_SCHEMAS_REF + rootName, version);
  }

  /**
   * Makes the member's discriminator property its own: a required string pinned to the member's
   * type names. Idempotent, and mutates the defined component in place so every cached reference to
   * it sees the same shape.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void pinMember(
      Union union,
      Member member,
      Schema<?> resolved,
      AnnotatedType type,
      ModelConverterContext context) {
    String memberName = componentName(resolved, decorated(type, nameOf(member.type())));
    Schema model = context.getDefinedModels().get(memberName);
    if (model == null) {
      throw new IllegalStateException(
          "Union member " + member.type().getName() + " must resolve to a named component");
    }
    String rootRef = Components.COMPONENTS_SCHEMAS_REF + decorated(type, nameOf(union.type()));
    if (model.getAllOf() != null) {
      for (Object part : model.getAllOf()) {
        if (rootRef.equals(((Schema<?>) part).get$ref())) {
          throw new IllegalStateException(
              "Union member "
                  + member.type().getName()
                  + " must not compose its root "
                  + union.type().getName()
                  + " with allOf");
        }
      }
    }

    String property = union.property();
    Map<String, Schema> properties = model.getProperties();
    Schema current = properties == null ? null : properties.get(property);
    if (current == null && union.include() == JsonTypeInfo.As.EXISTING_PROPERTY) {
      // EXISTING_PROPERTY: Jackson writes the type id only through the bean's own property.
      throw new IllegalStateException(
          "Union member "
              + member.type().getName()
              + " must declare the discriminator property '"
              + property
              + "' (include = EXISTING_PROPERTY)");
    }

    List<String> names = member.names();
    if (current != null && current.get$ref() == null) {
      if (!isString(current)) {
        throw new IllegalStateException(
            "Discriminator '" + property + "' of " + member.type().getName() + " must be a string");
      }
      List<?> declaredValues = current.getEnum();
      if (declaredValues != null && !declaredValues.containsAll(names)) {
        throw new IllegalStateException(
            "Discriminator '"
                + property
                + "' of "
                + member.type().getName()
                + " allows "
                + declaredValues
                + " but @JsonSubTypes names it "
                + names);
      }
      if (!names.equals(declaredValues)) {
        current.setEnum(new ArrayList<>(names));
      }
    } else {
      Schema pinned = new Schema<>();
      pinned.setSpecVersion(model.getSpecVersion());
      pinned.setTypes(new LinkedHashSet<>(List.of("string")));
      pinned.setType("string");
      pinned.setEnum(new ArrayList<>(names));
      if (current != null) {
        pinned.setDescription(current.getDescription());
      }
      Map<String, Schema> ordered = new LinkedHashMap<>();
      ordered.put(property, pinned);
      if (properties != null) {
        properties.forEach((name, schema) -> ordered.putIfAbsent(name, schema));
      }
      model.setProperties(ordered);
    }
    model.addRequiredItem(property);
  }

  private static boolean isString(Schema<?> schema) {
    return "string".equals(schema.getType())
        || (schema.getTypes() != null && schema.getTypes().contains("string"));
  }

  private static String componentName(Schema<?> resolved, String fallback) {
    if (resolved != null) {
      String ref = resolved.get$ref();
      if (ref != null && ref.startsWith(Components.COMPONENTS_SCHEMAS_REF)) {
        return ref.substring(Components.COMPONENTS_SCHEMAS_REF.length());
      }
      if (resolved.getName() != null) {
        return resolved.getName();
      }
    }
    // A member still being resolved higher up the stack (a recursive union) has no result yet,
    // but it is already defined under its name.
    return fallback;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> reference(String ref, SpecVersion version) {
    Schema reference = new Schema<>();
    reference.setSpecVersion(version == null ? SpecVersion.V30 : version);
    reference.set$ref(ref);
    return reference;
  }

  private static String nameOf(Class<?> type) {
    return TypeNameResolver.std.nameForType(TypeFactory.defaultInstance().constructType(type));
  }

  /** Mirrors Swagger's JsonView suffix so names agree with the components it defines. */
  private static String decorated(AnnotatedType type, String name) {
    if (type.getJsonViewAnnotation() == null || type.getJsonViewAnnotation().value().length == 0) {
      return name;
    }
    List<String> views = new ArrayList<>();
    for (Class<?> view : type.getJsonViewAnnotation().value()) {
      views.add(view.getSimpleName());
    }
    return name + "_" + String.join("-or-", views);
  }

  private static Class<?> rawClass(AnnotatedType type) {
    if (type.getType() == null) {
      return null;
    }
    return TypeFactory.defaultInstance().constructType(type.getType()).getRawClass();
  }

  private static List<Union> unionsListing(Class<?> type) {
    List<Union> owners = new ArrayList<>();
    Set<Class<?>> seen = new HashSet<>();
    Deque<Class<?>> pending = new ArrayDeque<>();
    pending.add(type);
    while (!pending.isEmpty()) {
      Class<?> current = pending.poll();
      if (current.getSuperclass() != null && seen.add(current.getSuperclass())) {
        pending.add(current.getSuperclass());
      }
      for (Class<?> parent : current.getInterfaces()) {
        if (seen.add(parent)) {
          pending.add(parent);
        }
      }
      if (current != type) {
        UNIONS.get(current).filter(union -> union.lists(type)).ifPresent(owners::add);
      }
    }
    return List.copyOf(owners);
  }

  record Member(Class<?> type, List<String> names) {}

  record Union(
      Class<?> type,
      String property,
      JsonTypeInfo.As include,
      List<Member> members,
      io.swagger.v3.oas.annotations.media.Schema schemaAnnotation) {

    static Union declaredOn(Class<?> type) {
      JsonTypeInfo info = type.getDeclaredAnnotation(JsonTypeInfo.class);
      JsonSubTypes subTypes = type.getDeclaredAnnotation(JsonSubTypes.class);
      if (info == null || subTypes == null || info.use() != JsonTypeInfo.Id.NAME) {
        return null;
      }
      JsonTypeInfo.As include = info.include();
      if (include != JsonTypeInfo.As.PROPERTY && include != JsonTypeInfo.As.EXISTING_PROPERTY) {
        return null;
      }
      String property =
          info.property().isEmpty() ? info.use().getDefaultPropertyName() : info.property();

      io.swagger.v3.oas.annotations.media.Schema schema =
          type.getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
      if (schema != null
          && (schema.oneOf().length > 0
              || schema.anyOf().length > 0
              || schema.allOf().length > 0
              || schema.subTypes().length > 0
              || !schema.discriminatorProperty().isEmpty()
              || schema.discriminatorMapping().length > 0)) {
        throw new IllegalStateException(
            type.getName()
                + " is a discriminated union: its members and type names come from"
                + " @JsonSubTypes. Remove oneOf/anyOf/allOf/subTypes/discriminator* from its @Schema.");
      }

      List<Member> members = new ArrayList<>();
      Set<Class<?>> memberTypes = new LinkedHashSet<>();
      Set<String> allNames = new HashSet<>();
      for (JsonSubTypes.Type subType : subTypes.value()) {
        Class<?> memberType = subType.value();
        if (memberType == type) {
          continue;
        }
        if (!type.isAssignableFrom(memberType)) {
          throw new IllegalStateException(
              memberType.getName()
                  + " is listed in @JsonSubTypes of "
                  + type.getName()
                  + " but does not extend it");
        }
        if (!memberTypes.add(memberType)) {
          throw new IllegalStateException(
              memberType.getName() + " is listed twice in @JsonSubTypes of " + type.getName());
        }
        Set<String> names = new LinkedHashSet<>();
        if (!subType.name().isEmpty()) {
          names.add(subType.name());
        }
        Collections.addAll(names, subType.names());
        if (names.isEmpty()) {
          JsonTypeName typeName = memberType.getDeclaredAnnotation(JsonTypeName.class);
          if (typeName != null && !typeName.value().isEmpty()) {
            names.add(typeName.value());
          }
        }
        if (names.isEmpty()) {
          throw new IllegalStateException(
              memberType.getName()
                  + " in @JsonSubTypes of "
                  + type.getName()
                  + " needs an explicit type name");
        }
        for (String name : names) {
          if (!allNames.add(name)) {
            throw new IllegalStateException(
                "Type name '" + name + "' is used twice in @JsonSubTypes of " + type.getName());
          }
        }
        members.add(new Member(memberType, List.copyOf(names)));
      }
      if (members.isEmpty()) {
        throw new IllegalStateException("@JsonSubTypes of " + type.getName() + " lists no members");
      }
      if (type.isSealed()) {
        Set<Class<?>> permitted = Set.of(type.getPermittedSubclasses());
        if (!permitted.equals(memberTypes)) {
          throw new IllegalStateException(
              "Sealed union "
                  + type.getName()
                  + " permits "
                  + permitted
                  + " but @JsonSubTypes lists "
                  + memberTypes);
        }
      }
      return new Union(type, property, include, List.copyOf(members), schema);
    }

    boolean lists(Class<?> candidate) {
      return members.stream().anyMatch(member -> member.type() == candidate);
    }

    Member member(Class<?> candidate) {
      return members.stream()
          .filter(member -> member.type() == candidate)
          .findFirst()
          .orElseThrow();
    }
  }
}

package com.fabricmanagement.platform.realtime.app;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Publishes the frames of every live-event operation (CEDIT-05 §3.2) as what they are: each SSE
 * {@code data} line is the standard {@code ApiResponse} envelope of one frame DTO. An operation
 * marks itself with the {@value #EXTENSION} extension and lists the frame DTOs as the {@code anyOf}
 * of its {@code text/event-stream} content; this customizer adds one envelope schema per frame
 * ({@code ApiResponseLiveReadyDto} and the others) and makes the stream content the {@code anyOf}
 * of those envelopes, with the event-to-schema map in {@value #FRAMES_EXTENSION}. It is anyOf
 * because ready and invalidated share one shape. The streaming Java type is never a schema.
 */
@Component
public class LiveStreamOpenApiCustomizer implements OpenApiCustomizer {

  /** Extension that marks a live-event operation; its value names the frames in order. */
  public static final String EXTENSION = "x-fabric-live-stream";

  public static final String EVENT_STREAM = "text/event-stream";

  /** On the stream schema: SSE event name to the envelope schema of its data line. */
  public static final String FRAMES_EXTENSION = "x-fabric-live-frames";

  private static final String REF = "#/components/schemas/";

  /** SSE event name → frame data schema, in the order a client meets them. */
  private static final Map<String, String> FRAMES =
      Map.of(
          SseLiveChannel.READY, "LiveReadyDto",
          SseLiveChannel.INVALIDATED, "LiveInvalidatedDto",
          SseLiveChannel.CLOSED, "LiveClosedDto");

  private static final List<String> FRAME_ORDER =
      List.of(SseLiveChannel.READY, SseLiveChannel.INVALIDATED, SseLiveChannel.CLOSED);

  @Override
  @SuppressWarnings("rawtypes")
  public void customise(OpenAPI openApi) {
    if (openApi.getComponents() == null
        || openApi.getComponents().getSchemas() == null
        || openApi.getPaths() == null) {
      return;
    }
    Map<String, Schema> schemas = openApi.getComponents().getSchemas();
    if (!schemas.keySet().containsAll(FRAMES.values())) {
      return; // No live operation in this document.
    }
    SpecVersion version = openApi.getSpecVersion();
    List<Schema> envelopes = new ArrayList<>();
    for (String event : FRAME_ORDER) {
      String dataSchema = FRAMES.get(event);
      String name = "ApiResponse" + dataSchema;
      schemas.put(name, envelope(event, dataSchema, schemas.containsKey("ErrorDetail"), version));
      envelopes.add(ref(name, version));
    }
    openApi.getPaths().values().stream()
        .flatMap(path -> path.readOperations().stream())
        .filter(LiveStreamOpenApiCustomizer::isLiveStream)
        .forEach(operation -> publishFrames(operation, envelopes, version));
  }

  private static boolean isLiveStream(Operation operation) {
    return operation.getExtensions() != null && operation.getExtensions().containsKey(EXTENSION);
  }

  @SuppressWarnings("rawtypes")
  private static void publishFrames(
      Operation operation, List<Schema> envelopes, SpecVersion version) {
    ApiResponse ok = operation.getResponses() == null ? null : operation.getResponses().get("200");
    if (ok == null) {
      return;
    }
    Content content = ok.getContent() == null ? new Content() : ok.getContent();
    MediaType stream =
        content.get(EVENT_STREAM) == null ? new MediaType() : content.get(EVENT_STREAM);
    // anyOf, not oneOf: ready and invalidated data have the same shape and are told apart by the
    // SSE event line, not by the JSON; a valid ready body matches both (CEDIT-05 R3).
    Schema<Object> frames = schema(version);
    frames.setAnyOf(new ArrayList<>(envelopes));
    frames.setDescription(
        "The JSON on the `data:` line of one frame. The SSE `event:` line names the frame and"
            + " its schema (see x-fabric-live-frames): `ready`: ApiResponseLiveReadyDto,"
            + " `invalidated`: ApiResponseLiveInvalidatedDto, `closed`: ApiResponseLiveClosedDto."
            + " Lines starting with `:` are keepalive comments, not data. No `id:` line is sent.");
    Map<String, String> byEvent = new LinkedHashMap<>();
    for (String event : FRAME_ORDER) {
      byEvent.put(event, REF + "ApiResponse" + FRAMES.get(event));
    }
    frames.addExtension(FRAMES_EXTENSION, byEvent);
    stream.setSchema(frames);
    Content onlyStream = new Content();
    onlyStream.addMediaType(EVENT_STREAM, stream);
    ok.setContent(onlyStream);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> envelope(
      String event, String dataSchema, boolean errorKnown, SpecVersion version) {
    Map<String, Schema> properties = new LinkedHashMap<>();
    properties.put("success", typed("boolean", version));
    properties.put("data", ref(dataSchema, version));
    properties.put("message", typed("string", version));
    Schema warnings = typed("array", version);
    warnings.setItems(typed("string", version));
    properties.put("warnings", warnings);
    if (errorKnown) {
      properties.put("error", ref("ErrorDetail", version));
    }
    Schema timestamp = typed("string", version);
    timestamp.setFormat("date-time");
    properties.put("timestamp", timestamp);

    Schema envelope = typed("object", version);
    envelope.setDescription(
        "Standard ApiResponse envelope of the `" + event + "` frame, on the frame's `data:` line.");
    envelope.setProperties(properties);
    envelope.setRequired(new ArrayList<>(List.of("success", "data", "timestamp")));
    return envelope;
  }

  private static Schema<Object> ref(String name, SpecVersion version) {
    Schema<Object> reference = schema(version);
    reference.set$ref(REF + name);
    return reference;
  }

  private static Schema<Object> typed(String type, SpecVersion version) {
    Schema<Object> schema = schema(version);
    schema.setTypes(new LinkedHashSet<>(List.of(type)));
    schema.setType(type);
    return schema;
  }

  private static Schema<Object> schema(SpecVersion version) {
    Schema<Object> schema = new Schema<>();
    schema.setSpecVersion(version == null ? SpecVersion.V31 : version);
    return schema;
  }
}

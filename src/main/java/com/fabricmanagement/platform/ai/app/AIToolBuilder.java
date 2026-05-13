package com.fabricmanagement.platform.ai.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Tool Builder - Builds OpenAI function calling tools definition.
 *
 * <p>Defines available functions for FabricAI.
 */
public class AIToolBuilder {

  /** Get available tools for OpenAI function calling. */
  public static List<Map<String, Object>> getAvailableTools() {
    return List.of(
        buildTool(
            "check_product_stock",
            "Check stock quantity for a product by name. Use this when user asks about stock, inventory, or availability.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "productName",
                        Map.of(
                            "type",
                            "string",
                            "description",
                            "Product name or UID to check stock for. "
                                + "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', 'gabardin 30/1', '40/2 keten', '190 GSM cotton'). "
                                + "DO NOT translate or reformat numeric/symbolic codes like 30/1, 40/2, Ne 20/1, 16x12, 190 GSM, %100.")),
                "required", List.of("productName"))),
        buildTool(
            "smart_search",
            "INTELLIGENT SEARCH: Automatically detects entity type (Fiber/Yarn/Fabric) from query and searches accordingly. "
                + "Use this as PRIMARY search function when user asks about textile items. "
                + "Examples: 'cotton' → FIBER, 'cotton yarn' → YARN, 'gabardine' → FABRIC. "
                + "More efficient than separate searches - ONE function call, smart detection, lower token cost.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "query",
                        Map.of(
                            "type",
                            "string",
                            "description",
                            "Search query - automatically detects if it's Fiber, Yarn, or Fabric. "
                                + "Examples: 'cotton' (fiber), 'cotton yarn' (yarn), 'gabardine' (fabric), '30/1 gabardine' (fabric). "
                                + "Preserve technical textile specifications EXACTLY as user typed (30/1, 40/2, GSM, etc.).")),
                "required", List.of("query"))),
        buildTool(
            "search_products",
            "Search for products in the system. Use this when user specifically asks about products or when smart_search doesn't find results.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "query",
                        Map.of(
                            "type",
                            "string",
                            "description",
                            "Search query to find products. "
                                + "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', '40/2 keten'). "
                                + "DO NOT translate numeric/symbolic codes.")),
                "required", List.of("query"))),
        buildTool(
            "get_production_status",
            "Get current production status and active orders. Use this when user asks about production status, orders, or manufacturing.",
            Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of())),
        buildTool(
            "get_fiber_info",
            "Get detailed information about a fiber including composition, technical specifications, and status. Use this when user asks about a specific fiber.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "fiberId",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Fiber ID (UUID). Use this if user provides exact fiber ID."),
                        "fiberName",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Fiber name to search for. Preserve technical terms EXACTLY (e.g., 'Cotton 30/1', 'Polyester 40/2'). Use when user mentions a fiber name.")),
                "required", List.of())),
        buildTool(
            "search_fibers",
            "Search for fibers by name. Use this when user wants to find or list fibers.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "query",
                        Map.of(
                            "type", "string",
                            "description",
                                "Search query to find fibers. Preserve technical terms EXACTLY.")),
                "required", List.of("query"))),
        buildTool(
            "list_fiber_categories",
            "List all available fiber categories. Use this when user wants to create a fiber and needs to select a category.",
            Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of())),
        buildTool(
            "create_product",
            "Create a new product. Use this when user wants to create a product. REQUIRES USER CONFIRMATION.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "productType",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Product type: FIBER, YARN, FABRIC, CHEMICAL, or CONSUMABLE (required)"),
                        "unit",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Unit of measurement: kg, m, piece, liter, etc. (required)")),
                "required", List.of("productType", "unit"))),
        buildTool(
            "create_fiber",
            "Create a new fiber. USER-FRIENDLY: If productId is not provided, Product will be auto-created with type=FIBER. REQUIRES USER CONFIRMATION.",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "productId",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Product ID (UUID) - Optional. If not provided, Product will be auto-created with type=FIBER using the 'unit' parameter"),
                        "unit",
                            Map.of(
                                "type", "string",
                                "description",
                                    "Unit for Product (kg, ton, m, etc.) - Required if productId is not provided. Used when auto-creating Product"),
                        "fiberCategoryId",
                            Map.of(
                                "type", "string",
                                "description", "Fiber Category ID (UUID) - required"),
                        "fiberIsoCodeId",
                            Map.of(
                                "type", "string",
                                "description", "Fiber ISO Code ID (UUID) - required"),
                        "fiberName",
                            Map.of(
                                "type", "string",
                                "description", "Fiber name (required)"),
                        "remarks",
                            Map.of(
                                "type", "string",
                                "description", "Additional remarks (optional)")),
                "required", List.of("fiberCategoryId", "fiberIsoCodeId", "fiberName")
                // Note: productId OR unit is required (handled in AIFunctionCaller)
                )));
  }

  private static Map<String, Object> buildTool(
      String name, String description, Map<String, Object> parameters) {
    Map<String, Object> tool = new HashMap<>();
    tool.put("type", "function");

    Map<String, Object> function = new HashMap<>();
    function.put("name", name);
    function.put("description", description);
    function.put("parameters", parameters);

    tool.put("function", function);
    return tool;
  }
}

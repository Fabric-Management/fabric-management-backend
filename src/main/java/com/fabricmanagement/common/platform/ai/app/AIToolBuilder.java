package com.fabricmanagement.common.platform.ai.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Tool Builder - Builds OpenAI function calling tools definition.
 *
 * <p>Defines available functions for FabricAI.</p>
 */
public class AIToolBuilder {

    /**
     * Get available tools for OpenAI function calling.
     */
    public static List<Map<String, Object>> getAvailableTools() {
        return List.of(
            buildTool("check_material_stock", 
                "Check stock quantity for a material by name. Use this when user asks about stock, inventory, or availability.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "materialName", Map.of(
                            "type", "string",
                            "description", "Material name or UID to check stock for. " +
                                "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', 'gabardin 30/1', '40/2 keten', '190 GSM cotton'). " +
                                "DO NOT translate or reformat numeric/symbolic codes like 30/1, 40/2, Ne 20/1, 16x12, 190 GSM, %100."
                        )
                    ),
                    "required", List.of("materialName")
                )
            ),
            buildTool("search_materials",
                "Search for materials in the system. Use this when user asks about available materials or wants to find a material.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of(
                            "type", "string",
                            "description", "Search query to find materials. " +
                                "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', '40/2 keten'). " +
                                "DO NOT translate numeric/symbolic codes."
                        )
                    ),
                    "required", List.of("query")
                )
            ),
            buildTool("get_production_status",
                "Get current production status and active orders. Use this when user asks about production status, orders, or manufacturing.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                )
            ),
            buildTool("get_fiber_info",
                "Get detailed information about a fiber including composition, technical specifications, and status. Use this when user asks about a specific fiber.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "fiberId", Map.of(
                            "type", "string",
                            "description", "Fiber ID (UUID). Use this if user provides exact fiber ID."
                        ),
                        "fiberName", Map.of(
                            "type", "string",
                            "description", "Fiber name to search for. Preserve technical terms EXACTLY (e.g., 'Cotton 30/1', 'Polyester 40/2'). Use when user mentions a fiber name."
                        )
                    ),
                    "required", List.of()
                )
            ),
            buildTool("search_fibers",
                "Search for fibers by name. Use this when user wants to find or list fibers.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of(
                            "type", "string",
                            "description", "Search query to find fibers. Preserve technical terms EXACTLY."
                        )
                    ),
                    "required", List.of("query")
                )
            ),
            buildTool("create_material",
                "Create a new material. Use this when user wants to create a material. REQUIRES USER CONFIRMATION.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "materialType", Map.of(
                            "type", "string",
                            "description", "Material type: FIBER, YARN, FABRIC, CHEMICAL, or CONSUMABLE (required)"
                        ),
                        "unit", Map.of(
                            "type", "string",
                            "description", "Unit of measurement: kg, m, piece, liter, etc. (required)"
                        )
                    ),
                    "required", List.of("materialType", "unit")
                )
            ),
            buildTool("create_fiber",
                "Create a new fiber. Use this when user wants to create a fiber. Material must exist with type=FIBER. REQUIRES USER CONFIRMATION.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "materialId", Map.of(
                            "type", "string",
                            "description", "Material ID (UUID) - Material must exist with type=FIBER (required)"
                        ),
                        "fiberCategoryId", Map.of(
                            "type", "string",
                            "description", "Fiber Category ID (UUID) - required"
                        ),
                        "fiberName", Map.of(
                            "type", "string",
                            "description", "Fiber name (required)"
                        ),
                        "fiberGrade", Map.of(
                            "type", "string",
                            "description", "Fiber grade (optional)"
                        ),
                        "fineness", Map.of(
                            "type", "number",
                            "description", "Fineness value (optional)"
                        ),
                        "lengthMm", Map.of(
                            "type", "number",
                            "description", "Length in millimeters (optional)"
                        ),
                        "strengthCndTex", Map.of(
                            "type", "number",
                            "description", "Strength in cN/dtex (optional)"
                        ),
                        "elongationPercent", Map.of(
                            "type", "number",
                            "description", "Elongation percentage (optional)"
                        ),
                        "remarks", Map.of(
                            "type", "string",
                            "description", "Additional remarks (optional)"
                        )
                    ),
                    "required", List.of("materialId", "fiberCategoryId", "fiberName")
                )
            )
        );
    }

    private static Map<String, Object> buildTool(String name, String description, Map<String, Object> parameters) {
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


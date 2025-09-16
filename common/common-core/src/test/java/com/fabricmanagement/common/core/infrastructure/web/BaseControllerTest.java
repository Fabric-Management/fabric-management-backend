package com.fabricmanagement.common.core.infrastructure.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for BaseController to verify compilation and structure.
 */
class BaseControllerTest {

    @Test
    void testBaseControllerClassExists() {
        // Verify that BaseController class is properly defined
        assertNotNull(BaseController.class);
        assertTrue(BaseController.class.isAnnotationPresent(lombok.RequiredArgsConstructor.class) ||
                  java.lang.reflect.Modifier.isAbstract(BaseController.class.getModifiers()),
                  "BaseController should be abstract or have RequiredArgsConstructor");
    }

    @Test
    void testBaseControllerMethods() {
        // Verify that BaseController has the expected REST methods
        var methods = BaseController.class.getDeclaredMethods();
        assertTrue(methods.length > 0, "BaseController should have declared methods");

        // Check for key REST method names
        var methodNames = java.util.Arrays.stream(methods)
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());

        assertTrue(methodNames.contains("create"), "Should have create method");
        assertTrue(methodNames.contains("update"), "Should have update method");
        assertTrue(methodNames.contains("getById"), "Should have getById method");
        assertTrue(methodNames.contains("getAll"), "Should have getAll method");
        assertTrue(methodNames.contains("delete"), "Should have delete method");
    }

    @Test
    void testBaseControllerIsAbstract() {
        // Verify that BaseController is abstract (cannot be instantiated directly)
        assertTrue(java.lang.reflect.Modifier.isAbstract(BaseController.class.getModifiers()),
                  "BaseController should be abstract");
    }
}

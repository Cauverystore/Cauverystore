package com.cauverystore.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every @Entity must have its @Id on an instance field.
 *
 * <h2>Why this needs a test</h2>
 *
 * An annotation binds to whatever declaration follows it. Add a constant to the top of an
 * entity - as happened twice here, with the compliance-status strings on GstInvoice and the
 * entry-type strings on TcsRecord - and it slots in between @Id and the field, leaving @Id on a
 * public static String. That compiles cleanly. Every test passes. Hibernate then ignores the
 * static, concludes the entity has no identifier, and refuses to build the
 * EntityManagerFactory, which takes the entire application down at startup: no login, no
 * checkout, no site.
 *
 * The failure appears in a deploy log as a wall of Spring stack trace with the real cause a
 * hundred lines down, and it costs a production outage to find. This turns it into a test
 * failure naming the class.
 *
 * It reads the compiled classes rather than the source, so it sees exactly what Hibernate will.
 */
class EntityIdentifierTest {

    @Test
    void everyEntityShouldHaveItsIdOnAnInstanceField() throws Exception {
        List<Class<?>> entities = loadEntities();
        assertTrue(entities.size() > 50,
                "expected to find the entity classes; found " + entities.size()
                        + ", so this test is not actually checking anything");

        List<String> broken = new ArrayList<>();
        for (Class<?> type : entities) {
            Field id = findIdField(type);
            if (id == null) {
                broken.add(type.getSimpleName() + ": no @Id on any instance field, and none "
                        + "inherited - Hibernate will refuse to map it");
            } else if (Modifier.isStatic(id.getModifiers())) {
                broken.add(type.getSimpleName() + ": @Id is on the static field '" + id.getName()
                        + "'. A constant was almost certainly added between the annotation and "
                        + "the field it belongs to");
            }
        }

        assertTrue(broken.isEmpty(),
                "these entities cannot be mapped, and the application will not start:\n  "
                        + String.join("\n  ", broken));
    }

    /** The @Id may sit on a superclass - BaseEntity carries it for most of these. */
    private Field findIdField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c != type && !c.isAnnotationPresent(MappedSuperclass.class)
                    && !c.isAnnotationPresent(Entity.class)) {
                continue;
            }
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) return f;
            }
        }
        // A static carrying @Id would not be found above only if it is on an unrelated class;
        // check the type's own statics so the message can say what went wrong.
        for (Field f : type.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) return f;
        }
        return null;
    }

    private List<Class<?>> loadEntities() throws Exception {
        File dir = new File("target/classes/com/cauverystore/entities");
        assertTrue(dir.isDirectory(), "compiled entities not found at " + dir.getAbsolutePath());

        List<Class<?>> entities = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
        for (File f : files == null ? new File[0] : files) {
            String name = "com.cauverystore.entities."
                    + f.getName().substring(0, f.getName().length() - ".class".length());
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            if (type.isAnnotationPresent(Entity.class)) entities.add(type);
        }
        return entities;
    }
}

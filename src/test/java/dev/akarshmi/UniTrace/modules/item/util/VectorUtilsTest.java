package dev.akarshmi.UniTrace.modules.item.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class VectorUtilsTest {

    @Test
    void literalFormat() {
        assertEquals("[1.0,0.5,-2.0]", VectorUtils.toVectorLiteral(new float[]{1f, 0.5f, -2f}));
    }

    @Test
    void literalRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.toVectorLiteral(null));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.toVectorLiteral(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.toVectorLiteral(new float[]{1f, Float.NaN}));
        assertThrows(IllegalArgumentException.class,
                () -> VectorUtils.toVectorLiteral(new float[]{Float.POSITIVE_INFINITY}));
    }

    @Test
    void usabilityAndDimension() {
        assertTrue(VectorUtils.isUsable(new float[]{0f, 1f}));
        assertFalse(VectorUtils.isUsable(null));
        assertFalse(VectorUtils.isUsable(new float[0]));
        assertFalse(VectorUtils.isUsable(new float[]{0f, 0f}));
        assertFalse(VectorUtils.isUsable(new float[]{1f, Float.NaN}));
        assertTrue(VectorUtils.isValid(new float[]{1f, 2f, 3f}, 3));
        assertFalse(VectorUtils.isValid(new float[]{1f, 2f, 3f}, 4));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.requireDimension(new float[2], 3, "text"));
        assertDoesNotThrow(() -> VectorUtils.requireDimension(new float[3], 3, "text"));
    }

    @Test
    void cosine() {
        assertEquals(1.0, VectorUtils.cosineSimilarity(new float[]{1, 2, 3}, new float[]{2, 4, 6}), 1e-9);
        assertEquals(0.0, VectorUtils.cosineSimilarity(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
        assertEquals(-1.0, VectorUtils.cosineSimilarity(new float[]{1, 0}, new float[]{-1, 0}), 1e-9);
        assertEquals(0.0, VectorUtils.cosineSimilarity(new float[]{0, 0}, new float[]{1, 1}), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> VectorUtils.cosineSimilarity(new float[]{1}, new float[]{1, 2}));
    }

    @Test
    void safeCosineNeverThrows() {
        assertTrue(VectorUtils.safeCosine(null, new float[]{1f}).isEmpty());
        assertTrue(VectorUtils.safeCosine(new float[]{1f}, new float[]{1f, 2f}).isEmpty());
        assertTrue(VectorUtils.safeCosine(new float[]{0f}, new float[]{1f}).isEmpty());
        OptionalDouble ok = VectorUtils.safeCosine(new float[]{1f, 0f}, new float[]{1f, 0f});
        assertEquals(1.0, ok.getAsDouble(), 1e-9);
    }

    @Test
    void normalizeProducesUnitLength() {
        float[] n = VectorUtils.normalize(new float[]{3f, 4f});
        assertEquals(0.6f, n[0], 1e-6);
        assertEquals(0.8f, n[1], 1e-6);
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.normalize(new float[]{0f, 0f}));
    }
}

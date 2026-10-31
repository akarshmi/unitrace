package dev.akarshmi.UniTrace.modules.item.util;

import java.util.OptionalDouble;

/** Stateless helpers for embedding vectors. Never returns NaN; invalid input is rejected or reported as empty. */
public final class VectorUtils {

    private VectorUtils() {
    }

    /** True when the vector is non-null, non-empty, fully finite and not all zeros. */
    public static boolean isUsable(float[] v) {
        if (v == null || v.length == 0) {
            return false;
        }
        double sumSq = 0;
        for (float x : v) {
            if (!Float.isFinite(x)) {
                return false;
            }
            sumSq += (double) x * x;
        }
        return sumSq > 0;
    }

    public static boolean isValid(float[] v, int expectedDimension) {
        return isUsable(v) && v.length == expectedDimension;
    }

    public static void requireDimension(float[] v, int expectedDimension, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " vector is null");
        }
        if (v.length != expectedDimension) {
            throw new IllegalArgumentException(
                    name + " vector has dimension " + v.length + ", expected " + expectedDimension);
        }
    }

    /** pgvector text literal, e.g. "[0.1,0.2,0.3]". Rejects null, empty, NaN and infinite values. */
    public static String toVectorLiteral(float[] v) {
        if (v == null || v.length == 0) {
            throw new IllegalArgumentException("Cannot build a vector literal from a null/empty vector");
        }
        StringBuilder sb = new StringBuilder(v.length * 10 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (!Float.isFinite(v[i])) {
                throw new IllegalArgumentException("Vector contains a non-finite value at index " + i);
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public static float[] normalize(float[] v) {
        if (!isUsable(v)) {
            throw new IllegalArgumentException("Cannot normalise an invalid vector");
        }
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    /** Cosine similarity in [-1,1]. Throws on null/length mismatch; returns 0 when a vector has zero length. */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Vectors must not be null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector length mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        double cos = dot / (Math.sqrt(na) * Math.sqrt(nb));
        if (Double.isNaN(cos)) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, cos));
    }

    /** Cosine similarity, or empty when either side is missing/invalid or the dimensions differ. */
    public static OptionalDouble safeCosine(float[] a, float[] b) {
        if (!isUsable(a) || !isUsable(b) || a.length != b.length) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(cosineSimilarity(a, b));
    }
}

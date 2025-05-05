package ru.nms.diplom.shardsearch.utils;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class L2DistanceComputer {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    public static float l2Distance(float[] a, float[] b) {
        FloatVector sum = FloatVector.zero(SPECIES);

        for (int i = 0; i < 384; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            FloatVector diff = va.sub(vb);
            FloatVector sq = diff.mul(diff);
            sum = sum.add(sq);
        }

        return sum.reduceLanes(VectorOperators.ADD);
    }
}
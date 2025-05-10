package ru.nms.diplom.shardsearch.shard.similarity;

import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.search.similarities.BasicStats;

public class BM25LSimilarity extends SimilarityBase {
    private final float k1;
    private final float b;
    private final float delta;

    public BM25LSimilarity(float k1, float b, float delta) {
        this.k1 = k1;
        this.b = b;
        this.delta = delta;
    }

    @Override
    protected double score(BasicStats stats, double freq, double docLen) {
        float idf = (float) (Math.log(1 + (stats.getNumberOfDocuments() - stats.getDocFreq() + 0.5)
                / (stats.getDocFreq() + 0.5)));

        double avgdl = stats.getAvgFieldLength();
        double norm = 1 - b + b * (docLen / avgdl);
        double adjustedTf = (freq + delta) / (k1 * norm + freq + delta);

        return idf * (k1 + 1) * adjustedTf;
    }

    @Override
    public String toString() {
        return "BM25L(k1=" + k1 + ", b=" + b + ", delta=" + delta + ")";
    }
}

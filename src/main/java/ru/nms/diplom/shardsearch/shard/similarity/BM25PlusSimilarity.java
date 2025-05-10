package ru.nms.diplom.shardsearch.shard.similarity;

import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.search.similarities.BasicStats;

public class BM25PlusSimilarity extends SimilarityBase {
    private final double k1;
    private final double b;
    private final double delta;

    public BM25PlusSimilarity(double k1, double b, double delta) {
        this.k1 = k1;
        this.b = b;
        this.delta = delta;
    }

    @Override
    protected double score(BasicStats stats, double freq, double docLen) {
        double idf = (Math.log(1 + (stats.getNumberOfDocuments() - stats.getDocFreq() + 0.5)
                / (stats.getDocFreq() + 0.5)));

        double avgdl = stats.getAvgFieldLength();
        double norm = 1 - b + b * (docLen / avgdl);
        double tf = freq / (freq + k1 * norm);

        return idf * ((k1 + 1) * tf + delta);
    }

    @Override
    public String toString() {
        return "BM25+(k1=" + k1 + ", b=" + b + ", delta=" + delta + ")";
    }
}

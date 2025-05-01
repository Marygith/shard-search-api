package ru.nms.diplom.shardsearch.config;

import java.util.List;

public class ShardFileConfig {

    private List<ShardEntry> shards;

    public List<ShardEntry> getShards() {
        return shards;
    }

    public void setShards(List<ShardEntry> shards) {
        this.shards = shards;
    }

    public static class ShardEntry {
        private int id;
        private String type; // "lucene", "faiss", or "both"
        private String luceneIndexPath;
        private String passagesCsvPath;





        private String embeddingsCsvPath;

        // Getters
        public int getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getLuceneIndexPath() {
            return luceneIndexPath;
        }
        public String getEmbeddingsCsvPath() {
            return embeddingsCsvPath;
        }
        public String getPassagesCsvPath() {
            return passagesCsvPath;
        }

        // Setters
        public void setId(int id) {
            this.id = id;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setLuceneIndexPath(String luceneIndexPath) {
            this.luceneIndexPath = luceneIndexPath;
        }

        public void setPassageCsvPath(String passagesCsvPath) {
            this.passagesCsvPath = passagesCsvPath;
        }
        public void setPassagesCsvPath(String passagesCsvPath) {
            this.passagesCsvPath = passagesCsvPath;
        }



        public void setEmbeddingsCsvPath(String embeddingsCsvPath) {
            this.embeddingsCsvPath = embeddingsCsvPath;
        }
        @Override
        public String toString() {
            return "ShardEntry{" +
                    "id=" + id +
                    ", type='" + type + '\'' +
                    ", luceneIndexPath='" + luceneIndexPath + '\'' +
                    ", passagesCsvPath='" + passagesCsvPath + '\'' +
                    ", embeddingsCsvPath='" + embeddingsCsvPath + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ShardFileConfig{" +
                "shards=" + shards +
                '}';
    }
}

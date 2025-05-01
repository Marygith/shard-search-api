package ru.nms.diplom.shardsearch.config;

public class ShardConfigEntry {
    private int id;
    private String type; // <-- Add this
    private String luceneIndexPath;
    private String csvPath;

    // Getters and setters
    public int getId() { return id; }
    public String getType() { return type; }
    public String getLuceneIndexPath() { return luceneIndexPath; }
    public String getCsvPath() { return csvPath; }

    public void setId(int id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setLuceneIndexPath(String luceneIndexPath) { this.luceneIndexPath = luceneIndexPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }
}

package ru.nms.diplom.shardsearch.model;

public enum IndexType {
    LUCENE(0),
    VECTOR(1);

    private final int number;


    IndexType(int number) {
        this.number = number;
    }

    public static IndexType fromNumber(int number) {
        return switch (number) {
            case 0 -> LUCENE;
            case 1 -> VECTOR;
            default -> throw new IllegalStateException("Unexpected index type: " + number);
        };
    }

    public int getNumber() {
        return number;
    }
}

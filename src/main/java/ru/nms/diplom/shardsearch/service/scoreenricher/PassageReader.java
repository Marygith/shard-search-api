package ru.nms.diplom.shardsearch.service.scoreenricher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class PassageReader {
    private final Map<Integer, float[]> idToVector;
    private final int vectorSize;

    public PassageReader(String pathToFile, int vectorSize) {
        this.vectorSize = vectorSize;
        this.idToVector = new HashMap<>();
        try {
            loadCSV(pathToFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadCSV(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != vectorSize + 1) {
                    throw new IllegalArgumentException("Invalid vector length for line: " + line);
                }

                Integer id = Integer.parseInt(parts[0]);
                var vector = new float[vectorSize];
                for (int i = 0; i < vectorSize; i++) {
                    vector[i] = Float.parseFloat(parts[i + 1]);
                }
                idToVector.put(id, vector);
            }
        }
    }

    public float[] getVectorById(Integer id) {
        return idToVector.get(id);
    }
}

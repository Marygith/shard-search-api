package ru.nms.diplom.shardsearch.service.scoreenricher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class PassageReader {

    private final Map<Integer, List<Float>> idToVector;
    private final String pathToFile;
    private final int vectorSize;

    public PassageReader(String pathToFile, int vectorSize) {
        this.pathToFile = pathToFile;
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
            String line = br.readLine(); // Skip header

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != vectorSize + 1) {
                    throw new IllegalArgumentException("Invalid vector length for line: " + line);
                }

                // Parse ID and vector
                Integer id = Integer.parseInt(parts[0]);
                var vector = new ArrayList<Float>();
                for (int i = 0; i < vectorSize; i++) {
                    vector.add(Float.parseFloat(parts[i + 1]));
                }

                // Store in the map
                idToVector.put(id, vector);
            }
        }
    }

    public List<Float> getVectorById(Integer id) {
        return idToVector.get(id);
    }

//    public static void main(String[] args) {
//        PassageReader reader = new PassageReader();
//
//        try {
//            // Load the CSV file
//            reader.loadCSV(EMBEDDINGS_FILE);
//            System.out.println("CSV loaded successfully.");
//
//            // Example: Retrieve a vector by ID
//            Integer testId = 12345; // Replace with a valid ID
//            float[] vector = reader.getVectorById(testId);
//
//            if (vector != null) {
//                System.out.println("Vector for ID " + testId + ": " + Arrays.toString(vector));
//            } else {
//                System.out.println("No vector found for ID " + testId);
//            }
//
//        } catch (IOException e) {
//            System.err.println("Error loading CSV: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
}

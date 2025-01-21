package it.nicoloscialpi.mazegenerator.themes;

import org.bukkit.Material;

import java.util.*;

public class Theme {


    private final HashMap<Material, Integer> floorMaterialWeight;
    private final HashMap<Material, Integer> wallMaterialWeight;
    private final HashMap<Material, Integer> topMaterialWeight;

    public Theme() {
        floorMaterialWeight = new HashMap<>();
        wallMaterialWeight = new HashMap<>();
        topMaterialWeight = new HashMap<>();
    }

    public Material getRandomMaterial(HashMap<Material, Integer> materialIntegerHashMap) {
        if (materialIntegerHashMap.isEmpty()) {
            return Material.STONE;
        }

        ArrayList<Map.Entry<Material, Integer>> entryList = new ArrayList<>(materialIntegerHashMap.entrySet());
        ArrayList<Map.Entry<Material, Integer>> cumulativeEntryList = new ArrayList<>();
        ArrayList<Map.Entry<Material, Double>> cumulativeNormalizedEntryList = new ArrayList<>();


        int totalWeight = 0;
        entryList.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<Material, Integer> entry : entryList) {
            cumulativeEntryList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), totalWeight));
            totalWeight += entry.getValue();
        }

        for (Map.Entry<Material, Integer> entry : cumulativeEntryList) {
            cumulativeNormalizedEntryList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue() / (double) totalWeight));
        }

        int size = cumulativeNormalizedEntryList.size();
        double random = Math.random();
        for (int i = 1; i < size; i++) {
            Map.Entry<Material, Double> materialDoubleEntry = cumulativeNormalizedEntryList.get(i);
            Map.Entry<Material, Double> materialDoubleEntryPrevious = cumulativeNormalizedEntryList.get(i - 1);
            if (random < materialDoubleEntry.getValue()) {
                return materialDoubleEntryPrevious.getKey();
            }
        }

        return cumulativeNormalizedEntryList.get(cumulativeNormalizedEntryList.size() - 1).getKey();
    }

    public Material getRandomFloorMaterial() {
        return getRandomMaterial(floorMaterialWeight);
    }

    public Material getRandomWallMaterial() {
        return getRandomMaterial(wallMaterialWeight);
    }

    public Material getRandomTopMaterial() {
        return getRandomMaterial(topMaterialWeight);
    }

    public void addFloorMaterialWeight(Material material, int weight) {
        floorMaterialWeight.put(material, weight);
    }

    public void addWallMaterialWeight(Material material, int weight) {
        wallMaterialWeight.put(material, weight);
    }

    public void addTopMaterialWeight(Material material, int weight) {
        topMaterialWeight.put(material, weight);
    }

    public void insertBySectionName(String sectionName, Material material, int weight) {
        String sectionNameLowerCase = sectionName.toLowerCase();
        switch (sectionNameLowerCase) {
            case "floor" -> addFloorMaterialWeight(material, weight);
            case "wall" -> addWallMaterialWeight(material, weight);
            case "top" -> addTopMaterialWeight(material, weight);
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("  Floor Materials:\n");
        appendMaterialWeightsToString(floorMaterialWeight, stringBuilder);

        stringBuilder.append("\n  Wall Materials:\n");
        appendMaterialWeightsToString(wallMaterialWeight, stringBuilder);

        stringBuilder.append("\n  Top Materials:\n");
        appendMaterialWeightsToString(topMaterialWeight, stringBuilder);

        return stringBuilder.toString();
    }

    private void appendMaterialWeightsToString(HashMap<Material, Integer> materialWeightMap, StringBuilder stringBuilder) {
        for (Map.Entry<Material, Integer> entry : materialWeightMap.entrySet()) {
            stringBuilder.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
    }
}
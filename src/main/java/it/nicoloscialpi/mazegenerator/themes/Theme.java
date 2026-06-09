package it.nicoloscialpi.mazegenerator.themes;

import org.bukkit.Material;

import java.util.*;

public class Theme {


private final HashMap<Material, Integer> floorMaterialWeight;
    private final HashMap<Material, Integer> wallMaterialWeight;
    private final HashMap<Material, Integer> topMaterialWeight;
    private final HashMap<Material, Integer> ladderMaterialWeight;

    private Material[] floorMaterials; private int[] floorCum; private int floorTotal = 0; private boolean floorDirty = true;
    private Material[] wallMaterials; private int[] wallCum; private int wallTotal = 0; private boolean wallDirty = true;
    private Material[] topMaterials; private int[] topCum; private int topTotal = 0; private boolean topDirty = true;
    private Material[] ladderMaterials; private int[] ladderCum; private int ladderTotal = 0; private boolean ladderDirty = true;

    public Theme() {
        floorMaterialWeight = new HashMap<>();
        wallMaterialWeight = new HashMap<>();
        topMaterialWeight = new HashMap<>();
        ladderMaterialWeight = new HashMap<>();
    }

    private static final ThreadLocal<java.util.Random> RNG = ThreadLocal.withInitial(java.util.Random::new);

    private void buildPicker(HashMap<Material, Integer> map, Picker p) {
        if (map.isEmpty()) { p.materials = null; p.cum = null; p.total = 0; return; }
        ArrayList<Map.Entry<Material, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort(Comparator.comparingInt(Map.Entry::getValue));
        int[] cum = new int[list.size()];
        Material[] mats = new Material[list.size()];
        int running = 0; int i = 0;
        for (Map.Entry<Material, Integer> e : list) {
            running += Math.max(1, e.getValue());
            mats[i] = e.getKey();
            cum[i] = running;
            i++;
        }
        p.materials = mats; p.cum = cum; p.total = running;
    }

    private Material pick(Picker p, String fallbackContext) {
        if (p.total <= 0 || p.materials == null) {
            System.getLogger("MazeGenerator").log(System.Logger.Level.WARNING, 
                "No materials configured for " + fallbackContext + ", falling back to STONE");
            return Material.STONE;
        }
        int r = RNG.get().nextInt(p.total);
        int idx = java.util.Arrays.binarySearch(p.cum, r + 1);
        if (idx < 0) idx = -idx - 1;
        if (idx < 0) idx = 0; if (idx >= p.materials.length) idx = p.materials.length - 1;
        return p.materials[idx];
    }

    private static class Picker { Material[] materials; int[] cum; int total; }

    private final Picker floorPicker = new Picker();
    private final Picker wallPicker = new Picker();
    private final Picker topPicker = new Picker();
    private final Picker ladderPicker = new Picker();

    public Material getRandomFloorMaterial() { if (floorDirty) { buildPicker(floorMaterialWeight, floorPicker); floorDirty = false; } return pick(floorPicker, "floor"); }
    public Material getRandomWallMaterial() { if (wallDirty) { buildPicker(wallMaterialWeight, wallPicker); wallDirty = false; } return pick(wallPicker, "wall"); }
    public Material getRandomTopMaterial() { if (topDirty) { buildPicker(topMaterialWeight, topPicker); topDirty = false; } return pick(topPicker, "top"); }
    public Material getRandomLadderMaterial() { if (ladderDirty) { buildPicker(ladderMaterialWeight, ladderPicker); ladderDirty = false; } return pick(ladderPicker, "ladder"); }

    public void addFloorMaterialWeight(Material material, int weight) { floorMaterialWeight.put(material, weight); floorDirty = true; }
    public void addWallMaterialWeight(Material material, int weight) { wallMaterialWeight.put(material, weight); wallDirty = true; }
    public void addTopMaterialWeight(Material material, int weight) { topMaterialWeight.put(material, weight); topDirty = true; }
    public void addLadderMaterialWeight(Material material, int weight) { ladderMaterialWeight.put(material, weight); ladderDirty = true; }

    public void insertBySectionName(String sectionName, Material material, int weight) {
        String sectionNameLowerCase = sectionName.toLowerCase();
        switch (sectionNameLowerCase) {
            case "floor" -> addFloorMaterialWeight(material, weight);
            case "wall" -> addWallMaterialWeight(material, weight);
            case "top" -> addTopMaterialWeight(material, weight);
            case "ladder" -> addLadderMaterialWeight(material, weight);
        }
    }
}

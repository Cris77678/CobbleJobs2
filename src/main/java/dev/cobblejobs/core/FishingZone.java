package dev.cobblejobs.core;

import lombok.Data;

@Data
public class FishingZone {
    private String dimension = "minecraft:overworld";
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    public FishingZone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public boolean contains(double x, double y, double z, String dim) {
        return this.dimension.equals(dim) &&
               x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    public double getCenterX() { return minX + (maxX - minX) / 2.0; }
    public double getCenterY() { return minY + (maxY - minY) / 2.0; }
    public double getCenterZ() { return minZ + (maxZ - minZ) / 2.0; }
}

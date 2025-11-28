package it.nicoloscialpi.mazegenerator.maze;

public class CellGroupBuffer {
    public static final int BYTES_PER_CELL = Integer.BYTES * 4;
    private int[] data = new int[16];
    private int size = 0;

    public void add(int worldX, int worldY, int worldZ, int type) {
        ensureCapacity(size + 4);
        data[size++] = worldX;
        data[size++] = worldY;
        data[size++] = worldZ;
        data[size++] = type;
    }

    public int cellCount() {
        return size / 4;
    }

    public int[][] toCellArray() {
        int cells = cellCount();
        int[][] arr = new int[cells][4];
        int idx = 0;
        for (int i = 0; i < cells; i++) {
            arr[i][0] = data[idx++];
            arr[i][1] = data[idx++];
            arr[i][2] = data[idx++];
            arr[i][3] = data[idx++];
        }
        return arr;
    }

    public int[] raw() {
        return data;
    }

    public int size() {
        return size;
    }

    public int bytes() {
        return cellCount() * BYTES_PER_CELL;
    }

    public void clear() {
        size = 0;
    }

    private void ensureCapacity(int wanted) {
        if (wanted <= data.length) {
            return;
        }
        int newLen = Math.max(data.length * 2, wanted);
        int[] next = new int[newLen];
        System.arraycopy(data, 0, next, 0, size);
        data = next;
    }
}

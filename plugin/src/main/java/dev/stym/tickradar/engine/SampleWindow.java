package dev.stym.tickradar.engine;

public final class SampleWindow {

    public static final int MAX_CAPACITY = 600;
    private static final double PERCENTILE = 0.95;

    private double[] values;
    private double[] largest;
    private int next;
    private int size;

    public SampleWindow(int capacity) {
        values = new double[checkCapacity(capacity)];
    }

    public int capacity() {
        return values.length;
    }

    public int size() {
        return size;
    }

    public void add(double value) {
        values[next] = value;
        next = (next + 1) % values.length;
        size = Math.min(size + 1, values.length);
    }

    public void resize(int capacity) {
        if (checkCapacity(capacity) == values.length) {
            return;
        }
        double[] ordered = ordered();
        int kept = Math.min(ordered.length, capacity);
        double[] resized = new double[capacity];
        System.arraycopy(ordered, ordered.length - kept, resized, 0, kept);
        values = resized;
        size = kept;
        next = kept % capacity;
    }

    public RegionStats stats() {
        if (size == 0) {
            return RegionStats.EMPTY;
        }
        int kept = largestNeeded(size);
        double[] top = largest(kept);
        int filled = 0;
        double sum = 0;
        int start = (next - size + values.length) % values.length;
        for (int i = 0; i < size; i++) {
            double value = values[(start + i) % values.length];
            sum += value;
            if (filled < kept) {
                insertDescending(top, filled, value);
                filled++;
            } else if (value > top[kept - 1]) {
                insertDescending(top, kept - 1, value);
            }
        }
        return new RegionStats(sum / size, top[0], top[kept - 1], size);
    }

    static int largestNeeded(int count) {
        return count - (int) Math.ceil(PERCENTILE * count) + 1;
    }

    private double[] largest(int kept) {
        if (largest == null || largest.length < kept) {
            largest = new double[largestNeeded(values.length)];
        }
        return largest;
    }

    private static void insertDescending(double[] top, int position, double value) {
        int slot = position;
        while (slot > 0 && top[slot - 1] < value) {
            top[slot] = top[slot - 1];
            slot--;
        }
        top[slot] = value;
    }

    double[] ordered() {
        double[] ordered = new double[size];
        int start = (next - size + values.length) % values.length;
        for (int i = 0; i < size; i++) {
            ordered[i] = values[(start + i) % values.length];
        }
        return ordered;
    }

    private static int checkCapacity(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Window capacity must be between 1 and " + MAX_CAPACITY + ": " + capacity);
        }
        return capacity;
    }
}

package me.griffin.robotcontrolapp.autonomous.vfh.structs;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class hist_t {
    public int alpha;
    public int sectors;
    public double threshold;
    public double damping_constant;
    public double density_a;
    public double density_b;
    public int[] densities;
}

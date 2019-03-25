package me.griffin.robotcontrolapp.autonomous.vfh.structs;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class grid_t {
    public int dimension; /* Dimension in number of cells. */
    public int resolution; /* Centimeters per cell. */
    public int[] cells; /* The obstacle density in each cell. */
}

package me.griffin.robotcontrolapp.autonomous.vfh.structs;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class grid_t {
    public int dimension; /* Dimension in number of cells. */
    public int resolution; /* Centimeters per cell. */
    public int[] cells; /* The obstacle density in each cell. */

    public grid_t(int dimension, int resolution) {
        // TODO: Also `assert` that the resolution is within some reasonable values (???).
        assert (dimension % 2 == 1);

        this.dimension = dimension;
        this.resolution = resolution;
        this.cells = new int[dimension * dimension];

        // Initial value, C_0 = 0.
        for (int i = 0; i < dimension; ++i) {
            for (int j = 0; j < dimension; ++j) {
                this.cells[i * dimension + j] = 0;
            }
        }
    }
}

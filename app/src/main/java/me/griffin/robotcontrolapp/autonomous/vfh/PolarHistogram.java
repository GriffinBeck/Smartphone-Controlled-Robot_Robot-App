package me.griffin.robotcontrolapp.autonomous.vfh;

import me.griffin.robotcontrolapp.autonomous.vfh.structs.grid_t;
import me.griffin.robotcontrolapp.autonomous.vfh.structs.hist_t;

import static java.lang.Math.atan2;
import static java.lang.Math.floor;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class PolarHistogram {
    //
// Polar histogram-related functions.
//

    hist_t hist_init(int alpha, double threshold, double density_a, double density_b) {
        /* Create a histogram pointer and allocate memory to it. */
        /*hist_t * hist = malloc(sizeof(hist_t));

        // Is there enough memory for the histogram?
        if (NULL == hist) {
            free(hist);
            return NULL;
        }*/

        // TODO: `assert` that alpha is a divider of 360.

        /* Initialize the histogram parameters. */
        hist_t hist = new hist_t();
        hist.alpha = alpha;
        hist.sectors = 360 / alpha;
        hist.threshold = threshold;
        hist.densities = new int[hist.sectors];
        if (hist.densities == null) {
            //free(hist);
            return null;
        }

        /* Initialize all densities to 0. */
        for (int i = 0; i < hist.sectors; ++i) {
            hist.densities[i] = 0;
        }

        return hist;
    }

    void hist_update(hist_t hist, grid_t grid) {
        int dim = grid.dimension;
        double dens_a = hist.density_a;
        double dens_b = hist.density_b;

        /* Calculate densities based on grid. */
        for (int i = 0; i < dim; ++i) {
            for (int j = 0; j < dim; ++j) {

                /* Calculate the angular position (beta) of this cell. */
                double beta = atan2((double) (j - dim / 2), (double) (i - dim / 2));

                /* Calculate the obstacle density of this cell. */
                double density = pow(grid.cells[i * dim + j], 2);
                density *= dens_a - dens_b * sqrt(pow(i - dim / 2, 2) + pow(j - dim / 2, 2));

                /* Add density to respective point in the histogram. */
                hist.densities[(int) floor(beta / hist.alpha)] += density;
            }
        }
    }

}

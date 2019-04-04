package me.griffin.robotcontrolapp.autonomous.vfh;

import me.griffin.robotcontrolapp.autonomous.vfh.structs.grid_t;
import me.griffin.robotcontrolapp.autonomous.vfh.structs.range_measure_t;

import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.sin;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class HistogramGrid {

    int grid_update(grid_t grid, int pos_x, int pos_y, final range_measure_t data) {
        if (grid == null) return 0;
        if (grid.cells == null) return 0;

        /*
         ** Transform each sensor reading into cartesian coordinates and increase the
         ** corresponding cell's obstacle density.
         **
         ** Polar to cartesian:
         **   (r, o) . (r * cos(x), r * sin(y))
         **
         ** Remember that cos() and sin() expect angles in RADIANS, not DEGREES.
         */
        double cells = data.distance / grid.resolution;

        int new_x = pos_x + (int) floor(cells * cos(data.direction * Math.PI / 180));
        int new_y = pos_y + (int) floor(cells * sin(data.direction * Math.PI / 180));

        /* Is this point inside the grid? (to avoid overflows) */
        if (new_x < grid.dimension && new_y < grid.dimension) {
            grid.cells[new_x * grid.dimension + new_y] += 1;
        }

        return 1;
    }

    /**
     * Author: Griffin
     * Adapted from grid_update
     *
     * @param grid   reference to grid object
     * @param pos_x  position x of robot relative to grid
     * @param pos_y  position y of robot relative to grid
     * @param pointX detected object point x relative to robot
     * @param pointY detected object point y relative to robot
     * @return 0 if failed 1 if success
     */
    int gridUpdateCartesian(grid_t grid, int pos_x, int pos_y, double pointX, double pointY) {
        if (grid == null) return 0;
        if (grid.cells == null) return 0;

        //Converts from parts of meter to uniform int ie. 2.3 m to 23 meaning a resolution of 10 points per meter
        int new_pointX = (int) (pointX * grid.resolution) + pos_x;
        int new_pointY = (int) (pointY * grid.resolution) + pos_y;

        /* Is this point inside the grid? (to avoid overflows) */
        if (pointX < grid.dimension && pointY < grid.dimension) {
            grid.cells[new_pointX * grid.dimension + new_pointY] += 1;
        }

        return 1;
    }

    /* TODO: Finish implementing get_moving_window. */
    grid_t get_moving_window(grid_t grid, int current_x, int current_y, int dimension) {
        /*
         ** Create a window with dimension `dimension` and the same resolution as grid.
         **
         ** If grid_init returns NULL, exit the function.
         */
        grid_t moving_window = new grid_t(dimension, grid.resolution);

        if (moving_window != null) {

            /* Populate moving_window's cells with the values of the ones in grid. */
            // TODO: Probably it is best to point directly to the values in the original grid?
            for (int i = 0; i < dimension; ++i) {
                for (int j = 0; j < dimension; ++j) {

                    /* x and y are the center coordinates of the body with sensors. */
                    int grid_i = i + current_x + (dimension - 1) / 2;
                    int grid_j = j + current_y + (dimension - 1) / 2;

                    /* Copy the information from the grid to the moving window. */
                    if (grid_i < grid.dimension && grid_j < grid.dimension) {
                        moving_window.cells[i * dimension + j] = grid.cells[grid_i * grid.dimension + grid_j];
                    }
                }
            }
        } else {
            //free(moving_window);
            return null;
        }

        return moving_window;
    }


}

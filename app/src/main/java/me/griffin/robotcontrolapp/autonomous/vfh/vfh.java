package me.griffin.robotcontrolapp.autonomous.vfh;

import me.griffin.robotcontrolapp.autonomous.vfh.structs.grid_t;
import me.griffin.robotcontrolapp.autonomous.vfh.structs.range_measure_t;

/**
 * Ported From Repository at https://github.com/agarie/vector-field-histogram
 */
public class vfh {
    grid_t grid_init(int dimension, int resolution) {
        // TODO: Also `assert` that the resolution is within some reasonable values (???).
        assert (dimension % 2 == 1);

        grid_t grid = new grid_t();

        if (grid == null) {
            //free(grid);
            return null;
        }

        grid.dimension = dimension;
        grid.resolution = resolution;
        grid.cells = new int[dimension * dimension];

        if (grid.cells == null) {
            //free(grid.cells);
            return null;
        }

        // Initial value, C_0 = 0.
        for (int i = 0; i < dimension; ++i) {
            for (int j = 0; j < dimension; ++j) {
                grid.cells[i * dimension + j] = 0;
            }
        }

        return grid;
    }

    int grid_update(grid_t grid, int pos_x, int pos_y, range_measure_t data) {
        if (grid == null) return 0;
        if (grid.cells == null) return 0;

        /*
         ** Transform each sensor reading into cartesian coordinates and increase the
         ** corresponding cell's obstacle density.
         **
         ** Polar to cartesian:
         **   (r, o) -> (r * cos(x), r * sin(y))
         **
         ** Remember that cos() and sin() expect angles in RADIANS, not DEGREES.
         */
        double cells = data.distance / grid.resolution;

        int new_x = pos_x + (int) Math.floor(cells * Math.cos(data.direction * Math.PI / 180));
        int new_y = pos_y + (int) Math.floor(cells * Math.sin(data.direction * Math.PI / 180));

        /* Is this point inside the grid? (to avoid overflows) */
        if (new_x < grid.dimension && new_y < grid.dimension) {
            grid.cells[new_x * grid.dimension + new_y] += 1;
        }

        return 1;
    }

    /* TODO: Finish implementing get_moving_window. */
    grid_t get_moving_window(grid_t grid, int current_x, int current_y, int dimension) {
        /*
         ** Create a window with dimension `dimension` and the same resolution as grid.
         **
         ** If grid_init returns null, exit the function.
         */
        grid_t moving_window = grid_init(dimension, grid.resolution);

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

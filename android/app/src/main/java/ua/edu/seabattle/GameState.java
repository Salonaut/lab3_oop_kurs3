package ua.edu.seabattle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure game rules, independent of Activity and HTTP. */
public final class GameState {
    public static final int SIZE = 10;
    private static final int[] LENGTHS = {5, 4, 3, 3, 2};
    private static final int[][] BASE = {
        {0, 0, 0, 1}, {2, 2, 0, 1}, {4, 0, 1, 0},
        {5, 5, 1, 0}, {9, 7, 0, 1}
    };
    private final Fleet player;
    private final Fleet enemy;
    private final char[][] playerShots = new char[SIZE][SIZE];
    private final char[][] enemyShots = new char[SIZE][SIZE];
    private boolean finished;
    private boolean pendingComputer;

    public GameState(int gameNumber) {
        player = new Fleet(gameNumber % 8);
        enemy = new Fleet((gameNumber + 3) % 8);
        for (int r = 0; r < SIZE; r++) {
            Arrays.fill(playerShots[r], '.');
            Arrays.fill(enemyShots[r], '.');
        }
    }

    public boolean isFinished() { return finished; }
    public boolean isPendingComputer() { return pendingComputer; }
    public boolean isEnemyShip(int r, int c) { return enemy.shipAt(r, c) != null; }
    public boolean isPlayerShip(int r, int c) { return player.shipAt(r, c) != null; }
    public char getPlayerShot(int r, int c) { return playerShots[r][c]; }
    public char getEnemyShot(int r, int c) { return enemyShots[r][c]; }

    /** @return true for a hit, false for a miss; throws for an invalid turn. */
    public boolean fireAtEnemy(int r, int c) {
        validate(r, c);
        if (finished || pendingComputer || enemyShots[r][c] != '.') {
            throw new IllegalStateException("Invalid player shot");
        }
        Ship ship = enemy.shipAt(r, c);
        enemyShots[r][c] = ship == null ? 'M' : 'H';
        if (ship != null && ship.isSunk(enemyShots)) {
            for (int[] cell : ship.cells) enemyShots[cell[0]][cell[1]] = 'S';
        }
        finished = enemy.allSunk(enemyShots);
        pendingComputer = !finished;
        return ship != null;
    }

    public JSONObject aiRequest() throws JSONException {
        if (!pendingComputer || finished) throw new IllegalStateException("No AI move pending");
        JSONArray rows = new JSONArray();
        for (char[] row : playerShots) rows.put(new String(row));
        JSONArray remaining = new JSONArray();
        for (Ship ship : player.ships) if (!ship.isSunk(playerShots)) remaining.put(ship.length);
        JSONObject obj = new JSONObject();
        obj.put("board", rows);
        obj.put("remaining", remaining);
        return obj;
    }

    /** @return true for a computer hit, false for a miss. */
    public boolean fireAtPlayer(int r, int c) {
        validate(r, c);
        if (!pendingComputer || finished || playerShots[r][c] != '.') {
            throw new IllegalStateException("Invalid computer shot");
        }
        Ship ship = player.shipAt(r, c);
        playerShots[r][c] = ship == null ? 'M' : 'H';
        if (ship != null && ship.isSunk(playerShots)) {
            for (int[] cell : ship.cells) playerShots[cell[0]][cell[1]] = 'S';
        }
        finished = player.allSunk(playerShots);
        pendingComputer = false;
        return ship != null;
    }

    private static void validate(int r, int c) {
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) throw new IllegalArgumentException("outside board");
    }

    private static class Ship {
        final int length;
        final List<int[]> cells = new ArrayList<>();
        Ship(int length) { this.length = length; }
        boolean contains(int r, int c) {
            for (int[] cell : cells) if (cell[0] == r && cell[1] == c) return true;
            return false;
        }
        boolean isSunk(char[][] shots) {
            for (int[] cell : cells) if (shots[cell[0]][cell[1]] != 'H' && shots[cell[0]][cell[1]] != 'S') return false;
            return true;
        }
    }

    private static class Fleet {
        final List<Ship> ships = new ArrayList<>();
        Fleet(int variation) {
            for (int i = 0; i < LENGTHS.length; i++) {
                Ship ship = new Ship(LENGTHS[i]);
                int[] origin = BASE[i];
                for (int n = 0; n < ship.length; n++) {
                    int r = origin[0] + n * origin[2];
                    int c = origin[1] + n * origin[3];
                    // All dihedral symmetries preserve separation and placement.
                    if ((variation & 1) != 0) c = SIZE - 1 - c;
                    if ((variation & 2) != 0) r = SIZE - 1 - r;
                    if ((variation & 4) != 0) { int tmp = r; r = c; c = tmp; }
                    ship.cells.add(new int[] {r, c});
                }
                ships.add(ship);
            }
        }
        Ship shipAt(int r, int c) {
            for (Ship ship : ships) if (ship.contains(r, c)) return ship;
            return null;
        }
        boolean allSunk(char[][] shots) {
            for (Ship ship : ships) if (!ship.isSunk(shots)) return false;
            return true;
        }
    }
}

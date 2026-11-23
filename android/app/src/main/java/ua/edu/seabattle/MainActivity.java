package ua.edu.seabattle;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Button[][] own = new Button[10][10];
    private final Button[][] opponent = new Button[10][10];
    private GameState game;
    private int gameNumber = 0;
    private boolean requesting = false;
    private TextView status;
    private Button retry;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildLayout();
        newGame();
    }

    private int dp(int v) { return (int) (getResources().getDisplayMetrics().density * v + .5f); }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(root);
        TextView heading = new TextView(this);
        heading.setText(R.string.app_name);
        heading.setTextSize(24);
        heading.setTextColor(Color.rgb(10, 60, 108));
        root.addView(heading);
        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button newGame = new Button(this);
        newGame.setText(R.string.new_game);
        newGame.setOnClickListener(v -> newGame());
        actions.addView(newGame);
        retry = new Button(this);
        retry.setText(R.string.retry_ai);
        retry.setOnClickListener(v -> askAi());
        retry.setVisibility(View.GONE);
        actions.addView(retry);
        root.addView(actions);
        addBoard(root, R.string.enemy_waters, opponent, true);
        addBoard(root, R.string.your_fleet, own, false);
        TextView legend = new TextView(this);
        legend.setText(R.string.legend);
        legend.setTextSize(13);
        root.addView(legend);
        setContentView(scroll);
    }

    private void addBoard(LinearLayout root, int label, Button[][] cells, boolean clickable) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(17);
        title.setPadding(0, dp(12), 0, dp(4));
        root.addView(title);
        GridLayout board = new GridLayout(this);
        board.setColumnCount(10);
        board.setRowCount(10);
        board.setUseDefaultMargins(false);
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                final int row = r, col = c;
                Button btn = new Button(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(r), GridLayout.spec(c));
                // 10 evenly spaced squares within the available screen width.
                int cell = Math.max(dp(24), (getResources().getDisplayMetrics().widthPixels - dp(56)) / 10);
                params.width = cell;
                params.height = cell;
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                btn.setLayoutParams(params);
                btn.setMinWidth(0);
                btn.setMinimumWidth(0);
                btn.setMinHeight(0);
                btn.setMinimumHeight(0);
                btn.setPadding(0, 0, 0, 0);
                btn.setTextSize(13);
                btn.setAllCaps(false);
                if (clickable) btn.setOnClickListener(v -> playerFire(row, col));
                else btn.setEnabled(false);
                cells[r][c] = btn;
                board.addView(btn);
            }
        }
        root.addView(board);
    }

    private void newGame() {
        game = new GameState(gameNumber++);
        requesting = false;
        retry.setVisibility(View.GONE);
        status.setText(R.string.your_turn);
        GameLogger.info("New game started: " + gameNumber);
        render();
    }

    private void playerFire(int r, int c) {
        if (requesting || game.isFinished() || game.isPendingComputer() || game.getEnemyShot(r, c) != '.') return;
        boolean hit = game.fireAtEnemy(r, c);
        GameLogger.info("Player shot at " + r + "," + c + ": " + (hit ? "hit" : "miss"));
        render();
        if (game.isFinished()) {
            status.setText(R.string.won);
            GameLogger.info("Player victory");
        } else {
            status.setText(hit ? R.string.player_hit : R.string.player_miss);
            askAi();
        }
    }

    private void askAi() {
        if (requesting || !game.isPendingComputer()) return;
        requesting = true;
        retry.setVisibility(View.GONE);
        final GameState requestedGame = game;
        final JSONObject request;
        try { request = requestedGame.aiRequest(); }
        catch (Exception ex) { requesting = false; GameLogger.error("Unable to prepare AI move", ex); return; }
        worker.execute(() -> {
            try {
                int[] chosen = AiClient.chooseMove(request);
                runOnUiThread(() -> {
                    if (game != requestedGame) return;
                    requesting = false;
                    try {
                        boolean hit = game.fireAtPlayer(chosen[0], chosen[1]);
                        GameLogger.info("AI shot at " + chosen[0] + "," + chosen[1] + ": " + (hit ? "hit" : "miss"));
                        render();
                        if (game.isFinished()) {
                            status.setText(R.string.lost);
                            GameLogger.info("AI victory");
                        } else {
                            String coordinate = String.valueOf((char) ('A' + chosen[1])) + (chosen[0] + 1);
                            status.setText(getString(hit ? R.string.computer_hit : R.string.computer_miss, coordinate));
                        }
                    } catch (Exception ex) { aiError(ex); }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (game != requestedGame) return;
                    requesting = false;
                    aiError(ex);
                });
            }
        });
    }

    private void aiError(Exception ex) {
        GameLogger.warn("AI move failed", ex);
        status.setText(R.string.ai_error);
        retry.setVisibility(View.VISIBLE);
        render();
    }

    private void render() {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                char e = game.getEnemyShot(r, c);
                char p = game.getPlayerShot(r, c);
                paint(opponent[r][c], e, false);
                paint(own[r][c], p, game.isPlayerShip(r, c));
                opponent[r][c].setEnabled(e == '.' && !game.isFinished() && !game.isPendingComputer());
            }
        }
    }

    private void paint(Button b, char shot, boolean ship) {
        int color = Color.rgb(190, 224, 244);
        String glyph = "";
        if (ship) { color = Color.rgb(77, 109, 130); glyph = "■"; }
        if (shot == 'M') { color = Color.rgb(215, 224, 232); glyph = "•"; }
        if (shot == 'H') { color = Color.rgb(248, 126, 92); glyph = "✕"; }
        if (shot == 'S') { color = Color.rgb(170, 53, 54); glyph = "✕"; }
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setText(glyph);
        b.setTextColor(Color.BLACK);
        b.setGravity(Gravity.CENTER);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}

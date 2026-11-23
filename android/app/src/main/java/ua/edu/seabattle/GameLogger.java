package ua.edu.seabattle;

import android.util.Log;

/** Centralized, tag-based Android logger. No secrets / personal data in logs. */
public final class GameLogger {
    private static final String TAG = "SeaBattle";
    private GameLogger() {}
    public static void info(String message) { Log.i(TAG, message); }
    public static void warn(String message, Throwable cause) { Log.w(TAG, message, cause); }
    public static void error(String message, Throwable cause) { Log.e(TAG, message, cause); }
}

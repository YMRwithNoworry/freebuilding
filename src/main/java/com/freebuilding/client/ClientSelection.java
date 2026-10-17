package com.freebuilding.client;

import com.freebuilding.selection.Selection;

public final class ClientSelection {
    private static volatile Selection selection;

    private ClientSelection() {
    }

    public static void set(Selection value) {
        selection = value;
    }

    public static Selection get() {
        return selection;
    }

    public static void clear() {
        selection = null;
    }
}

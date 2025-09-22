package com.lemore.data.utils;

import com.fs.starfarer.api.Global;

public class Local {
    public static String getString(String key) {
        return Global.getSettings().getString("AutomatedEscort", key);
    }
}

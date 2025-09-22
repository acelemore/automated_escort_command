package com.lemore.data.hullmods;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.lemore.data.utils.Constant;
import com.lemore.data.utils.Local;

public class EscortLevel extends com.fs.starfarer.api.combat.BaseHullMod {
    protected int level = 1;
    public EscortLevel(int _level) {
        // 默认构造函数
        this.level = _level;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.getCustomData().containsKey(Constant.LEADER_ESCORT_SCORE)) {
            ship.setCustomData(Constant.LEADER_ESCORT_SCORE, level);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        var shipMods = ship.getVariant().getHullMods();
        boolean isLeader = false;
        boolean hasOtherLevel = false;
        for (var mod : shipMods) {
            if (mod.startsWith("automated_escort_leader")) {
                isLeader = true;
            }
            if (mod.startsWith("automated_escort_level")) {
                var levelStr = mod.replace("automated_escort_level_", "");
                var l = getLevelFromString(levelStr);
                if (!(l == level)) {
                    hasOtherLevel = true; // 有其他等级的护送
                }
            }
        }
        if (isLeader && !hasOtherLevel) {
            return true;
        }
        return false;
    }

    private int getLevelFromString(String levelStr) {
        switch (levelStr) {
            case "light":
                return 1;
            case "medium":
                return 2;
            case "heavy":
                return 4;
            default:
                return -1;
        }
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        var shipMods = ship.getVariant().getHullMods();
        boolean isLeader = false;
        boolean hasOtherLevel = false;
        for (var mod : shipMods) {
            if (mod.startsWith("automated_escort_leader")) {
                isLeader = true;
            }
            if (mod.startsWith("automated_escort_level")) {
                hasOtherLevel = true;
                var levelStr = mod.replace("automated_escort_level_", "");
                var l = getLevelFromString(levelStr);
                if (!(l == level)) {
                    hasOtherLevel = true; // 有其他等级的护送
                }
            }
        }
        if (!isLeader) {
            return Local.getString(Constant.MSG_NOT_LEADER);
        }
        if (hasOtherLevel) {
            return Local.getString(Constant.MSG_TYPE_RESTRICT);
        }
        return "should not reach here";
    }
}

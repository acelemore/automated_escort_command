package com.lemore.data.utils;
import java.awt.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.mission.FleetSide;

public class CombatLog {
    public static final Color TEXT_COLOR = Global.getSettings().getColor("standardTextColor");
    public static final Color FRIEND_COLOR = Global.getSettings().getColor("textFriendColor");
    public static final Color ENEMY_COLOR = Global.getSettings().getColor("textEnemyColor");
    public static final Color HIGHLIGHT_COLOR = Color.CYAN;

    public static String getShipName(ShipAPI ship) {
        return ship.getName() + " (" + ship.getHullSpec().getHullNameWithDashClass() + ")";
    }

    public static void addLog(ShipAPI ship, String message) {
        DeployedFleetMemberAPI deployedMember = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER).getDeployedFleetMember(ship);
        if (deployedMember == null) return;
        String shipName = getShipName(ship);
        Object[] args = new Object[] {
            deployedMember,
            FRIEND_COLOR, shipName,
            TEXT_COLOR, ": ",
            HIGHLIGHT_COLOR, message
        };
        Global.getCombatEngine().getCombatUI().addMessage(1, args);
    }
}

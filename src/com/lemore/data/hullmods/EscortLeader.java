package com.lemore.data.hullmods;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.lemore.data.utils.Constant;
import com.lemore.data.utils.Local;

public class EscortLeader extends EscortTeamBase {
    public EscortLeader(String _team) {
        this.team = _team;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.getCustomData().containsKey(Constant.LEADER_ESCORT_TEAM)) {
            ship.setCustomData(Constant.LEADER_ESCORT_TEAM, team);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        // 队员/队长船插只能装一种
        var shipMods = ship.getVariant().getHullMods();
        for (var mod : shipMods) {
            if (mod.startsWith("automated_escort_member")) {
                return false;
            }
            if (mod.startsWith("automated_escort_leader_")) {
                if (mod.replace("automated_escort_leader_", "").equals(team)) {
                    continue; // skip self
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return Local.getString(Constant.MSG_TYPE_RESTRICT);
    }
}

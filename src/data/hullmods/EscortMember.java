package data.hullmods;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;

public class EscortMember extends EscortTeamBase {
    private static final Logger LOGGER = Global.getLogger(EscortMember.class);

    public EscortMember(String _team) {
        // 默认构造函数
        this.team = _team;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 好像没提供初始化的接口, 只能每帧都设置了
        if (!ship.getCustomData().containsKey(EscortDataKeys.MEMBER_ESCORT_SCORE.getValue())) {
            ShipAPI.HullSize hullSize = ship.getHullSize();
            int escortScore;
            switch (hullSize) {
                case FRIGATE:
                    escortScore = 1; // 护卫
                    break;
                case DESTROYER:
                    escortScore = 2;
                    break;
                case CRUISER:
                    escortScore = 4; // 中型护卫
                    break;
                default:
                    escortScore = 0; // 其他舰种无效
            }
            ship.setCustomData(EscortDataKeys.MEMBER_ESCORT_SCORE.getValue(), escortScore);
            ship.setCustomData(EscortDataKeys.ESCORT_TEAM.getValue(), team);
            LOGGER.info("Initialized escort score for ship: " + ship + " with score: " + escortScore + " and team: " + team);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        // 队员/队长船插只能装一种
        var shipMods = ship.getVariant().getHullMods();
        for (var mod : shipMods) {
            if (mod.startsWith("automated_escort_leader_")) {
                return false;
            }
            if (mod.startsWith("automated_escort_member_")) {
                if (mod.replace("automated_escort_member_", "").equals(team)) {
                    continue; // skip self
                } else {
                    return false;
                }
            }
        }
        var shipSize = ship.getHullSize();
        if (shipSize == ShipAPI.HullSize.CAPITAL_SHIP) {
            return false; // 主力舰不能执行护卫工作
        }
        return true;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        var shipSize = ship.getHullSize();
        if (shipSize == ShipAPI.HullSize.CAPITAL_SHIP) {
            return Global.getSettings().getString(EscortDataKeys.MSG_STRING_KEY.getValue(),
                    EscortDataKeys.MSG_NOT_APPLICABLE_ON_CAPITAL_SHIP.getValue());
        }
        return Global.getSettings().getString(EscortDataKeys.MSG_STRING_KEY.getValue(),
                EscortDataKeys.MSG_TYPE_RESTRICT.getValue());
    }
}

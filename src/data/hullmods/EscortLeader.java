package data.hullmods;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;


public class EscortLeader extends EscortTeamBase {
    private static final float LEADER_UPDATE_INTERVAL = 1f; // 先每秒运行看看效率
    private static final Logger LOGGER = Global.getLogger(EscortLeader.class);
    private static final float ASSIGN_DELAY = 10f; // 开场10秒后再开始分配任务, 免得船挤在一块

    public EscortLeader(String _team) {
        this.team = _team;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (Global.getCombatEngine().isCombatOver()) {
            return;
        }

        var fleet = Global.getCombatEngine().getFleetManager(ship.getOwner());
        var leaderFleetMember = fleet.getDeployedFleetMember(ship);
        CombatTaskManagerAPI taskManager = fleet.getTaskManager(false);
        if (taskManager.isFullAssault() || taskManager.isInFullRetreat()) {
            return; // 全面进攻或撤退状态, 不进行护卫分配
        }
        if (leaderFleetMember == null) {
            return; // 没有部署的舰船
        }
        float firstDeployTime = (float)ship.getCustomData().getOrDefault(EscortDataKeys.LEADER_FIRST_DEPLOY_TIME.getValue(), 0f);
        if (firstDeployTime == 0f) {
            // 第一次部署, 记录时间(包括暂停)
            ship.setCustomData(EscortDataKeys.LEADER_FIRST_DEPLOY_TIME.getValue(), Global.getCombatEngine().getTotalElapsedTime(true));
            return;
        } else if (Global.getCombatEngine().getTotalElapsedTime(true) - firstDeployTime < ASSIGN_DELAY) {
            // 还没到分配任务的时间
            return;
        }

        float lastUpdate = (float)ship.getCustomData().getOrDefault(EscortDataKeys.LEADER_LAST_UPDATE.getValue(), 0f);
        if (Global.getCombatEngine().getTotalElapsedTime(false) - lastUpdate < LEADER_UPDATE_INTERVAL) {
            return;
        }
        ship.setCustomData(EscortDataKeys.LEADER_LAST_UPDATE.getValue(), Global.getCombatEngine().getTotalElapsedTime(false));
        int escortScore = (int)Global.getCombatEngine().getCustomData().getOrDefault(EscortDataKeys.LEADER_ESCORT_SCORE.getValue(), -1);
        if (escortScore == -1) {
            // 如果没有安装类型船插, 那么根据舰船类型设置默认护卫分数
            ShipAPI.HullSize hullSize = ship.getHullSize();
            switch (hullSize) {
                case FRIGATE:
                    escortScore = 1; // 轻型护卫
                    break;
                case DESTROYER:
                    escortScore = 1;
                    break;
                case CRUISER:
                    escortScore = 2; // 中型护卫
                    break;
                case CAPITAL_SHIP:
                    escortScore = 4; // 重型护卫
                    break;
                default:
                    escortScore = 0; // 无护卫
            }
        }

        if (escortScore <= 0) {
            return;
        }

        if (ship.getCustomData().containsKey(EscortDataKeys.LEADER_CANCLE_CUSTOM_ESCORT.getValue())) {
            return;
        }

        
        var currentAssginmentInfo = taskManager.getAssignmentInfoForTarget(leaderFleetMember);
        if (currentAssginmentInfo == null) {
            LOGGER.info("No current assignment info found for ship: " + ship);
        } else {
            LOGGER.info("Current assignment info: " + currentAssginmentInfo.getType());
        }
        if (currentAssginmentInfo != null 
        && (currentAssginmentInfo.getType() != CombatAssignmentType.LIGHT_ESCORT
        && currentAssginmentInfo.getType() != CombatAssignmentType.HEAVY_ESCORT
        && currentAssginmentInfo.getType() != CombatAssignmentType.MEDIUM_ESCORT)) {
            // 如果被指派过一次防御任务, 那么需要标记这场战斗都不需要用自定义护卫AI了, aka安全词XD
            if (currentAssginmentInfo != null && currentAssginmentInfo.getType() == CombatAssignmentType.DEFEND) {
                ship.setCustomData(EscortDataKeys.LEADER_CANCLE_CUSTOM_ESCORT.getValue(), true);
            } 
            return;
        }
        // ==========================继续===============================
        gatherEscortMembers(ship, escortScore);
    }

    

    private void gatherEscortMembers(ShipAPI ship, int desireScore) {
        var fleet = Global.getCombatEngine().getFleetManager(ship.getOwner());
        if (fleet == null) {
            return;
        }
        var playerShip = Global.getCombatEngine().getPlayerShip();
        var deployMembers = fleet.getDeployedCopyDFM();
        var leaderFleetMember = fleet.getDeployedFleetMember(ship);
        CombatTaskManagerAPI taskManager = fleet.getTaskManager(false);
        // var allTasks = taskManager.getAllAssignments();
        // for (var task : allTasks) {
        //     LOGGER.info("=====================================");
        //     LOGGER.info("Task: " + task.getType() + ", target: " + task.getTarget() + ", members: " + task.getAssignedMembers());
        //     LOGGER.info("=====================================");
        // }
        if (leaderFleetMember == null) {
            return;
        }
        // 先查找自己已有的护卫成员
        var legalMembers = new ArrayList<DeployedFleetMemberAPI>();
        var assignInfo = taskManager.getAssignmentInfoForTarget(leaderFleetMember);
        var currentScore = 0;
        var largestMember = ShipAPI.HullSize.FRIGATE;
        if (assignInfo != null) {
            var currentEscortMembers = assignInfo.getAssignedMembers();
            for (var member : currentEscortMembers) {
                if (member == null) {
                    continue;
                }
                var memberShip = member.getShip();
                if (!memberShip.isAlive()) {
                    continue;
                }
                var memberEscortScore = (int)memberShip.getCustomData().getOrDefault(EscortDataKeys.MEMBER_ESCORT_SCORE.getValue(), 0);
                var memberEscortTeam = (String)memberShip.getCustomData().getOrDefault(EscortDataKeys.ESCORT_TEAM.getValue(), "");
                if (memberEscortScore <= 0 || !memberEscortTeam.equals(team)) {
                    // 有系统分配的船, 清空其命令
                    taskManager.orderSearchAndDestroy(member, false);
                    LOGGER.info("Member " + member + " has no escort score, removing from escort team.");
                    continue;
                }
                if (currentScore + memberEscortScore > desireScore) {
                    // 已经超过了护卫分数, 不再添加并清空命令
                    taskManager.orderSearchAndDestroy(member, false);
                    LOGGER.info("Member " + member + " exceeds desired escort score, removing.");
                    continue;
                }

                currentScore += memberEscortScore;
                legalMembers.add(member);
                if (memberShip.getHullSize().ordinal() > largestMember.ordinal()) {
                    largestMember = memberShip.getHullSize();
                }
            }
        }
        
        if(currentScore >= desireScore) {
            // 已经有足够的护卫成员了
            return;
        }
        var needScore = desireScore - currentScore;
        var minDistance = Float.MAX_VALUE;
        int bestScore = 0;
        DeployedFleetMemberAPI bestMember = null;
        for (var deployedMember : deployMembers) {
            if(deployedMember == null) {
                continue;
            }
            var deployedShip = deployedMember.getShip();
            if (deployedShip == null || deployedShip == ship || deployedShip == playerShip || !deployedShip.isAlive()) {
                continue;
            }
            int memberEscortScore = (int)deployedShip.getCustomData().getOrDefault(EscortDataKeys.MEMBER_ESCORT_SCORE.getValue(), 0);
            if (memberEscortScore > 0 && memberEscortScore <= needScore) {
                var shipAssignment = taskManager.getAssignmentFor(deployedShip);
                if (shipAssignment != null && shipAssignment.getType() != CombatAssignmentType.SEARCH_AND_DESTROY) {
                    // 当前已被指派其他任务
                    continue;
                }
                if (legalMembers.contains(deployedMember)) {
                    // 已在列表中
                    continue;
                }
                var escortTeam = (String)deployedShip.getCustomData().getOrDefault(EscortDataKeys.ESCORT_TEAM.getValue(), "");
                if (!escortTeam.equals(team)) {
                    // 不是同一队的
                    continue;
                }
                // 计算距离
                var distance = Misc.getDistanceSq(deployedShip.getLocation(), ship.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    bestMember = deployedMember;
                    bestScore = memberEscortScore;
                }
                
            }
        }
        if (bestMember != null) {
            legalMembers.add(bestMember);
            if (bestMember.getShip().getHullSize().ordinal() > largestMember.ordinal()) {
                largestMember = bestMember.getShip().getHullSize();
            }
        }
        var finalScore = currentScore + bestScore;
        
        // LOGGER.info(bestMember + " added to escort team: " + team + ", score: " + bestScore + ", final score: " + finalScore);
        if (finalScore > 0) {
            // 1分轻型护卫, 3分中型护卫, 6分以上重型护卫
            // var desireType = CombatAssignmentType.LIGHT_ESCORT;
            // if (finalScore >= 3) {  // 一艘驱逐一艘护卫
            //     desireType = CombatAssignmentType.MEDIUM_ESCORT;
            // } else if (finalScore >= 6) { // 一艘巡洋一艘驱逐
            //     desireType = CombatAssignmentType.HEAVY_ESCORT;
            // }

            // 原版似乎是根据最大舰船类型决定护卫类型
            // 虽然错误的护卫类型也能运作, 但是正确指定护卫类型可以减少系统自动分配的情况
            var desireType = CombatAssignmentType.LIGHT_ESCORT;
            if (largestMember == ShipAPI.HullSize.DESTROYER) {
                desireType = CombatAssignmentType.MEDIUM_ESCORT;
            } else if (largestMember == ShipAPI.HullSize.CRUISER || largestMember == ShipAPI.HullSize.CAPITAL_SHIP) {
                desireType = CombatAssignmentType.HEAVY_ESCORT;
            }

            var currentAssginmentInfo = taskManager.getAssignmentInfoForTarget(leaderFleetMember);
            // LOGGER.info("Current assignment info: " + currentAssginmentInfo);
            if (currentAssginmentInfo == null) {
                currentAssginmentInfo = taskManager.createAssignment(desireType, leaderFleetMember, false);
                // LOGGER.info("Created new assignment: " + currentAssginmentInfo);
            } else if (currentAssginmentInfo.getType() != desireType) {
                // 清除掉现有任务然后重建
                taskManager.removeAssignment(currentAssginmentInfo);
                currentAssginmentInfo = taskManager.createAssignment(desireType, leaderFleetMember, false);
                // LOGGER.info("Recreated assignment: " + currentAssginmentInfo);
            }
            taskManager.setAssignmentWeight(currentAssginmentInfo, 0); // 不知道是否能让系统不自动分配
            for (var member : legalMembers) {
                taskManager.giveAssignment(member, currentAssginmentInfo, false);
                // LOGGER.info("Assigned member: " + member + " to assignment: " + currentAssginmentInfo);
            }
        } else {
            // 没有可用的护卫成员, 清除任务
            var currentAssginmentInfo = taskManager.getAssignmentInfoForTarget(leaderFleetMember);
            if (currentAssginmentInfo != null) {
                taskManager.removeAssignment(currentAssginmentInfo);
                // LOGGER.info("Removed assignment: " + currentAssginmentInfo);
            }
        }
        return;
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
        return Global.getSettings().getString(EscortDataKeys.MSG_STRING_KEY.getValue(),
                EscortDataKeys.MSG_TYPE_RESTRICT.getValue());
    }
}

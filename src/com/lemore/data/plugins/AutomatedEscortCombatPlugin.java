package com.lemore.data.plugins;

import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.lemore.data.hullmods.EscortMember;
import com.lemore.data.utils.CombatLog;
import com.lemore.data.utils.Constant;
import com.lemore.data.utils.Local;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;


public class AutomatedEscortCombatPlugin implements EveryFrameCombatPlugin {
    private static final Logger LOGGER = Global.getLogger(EscortMember.class);
    private final IntervalUtil updateTimer = new IntervalUtil(1f, 2f);
    private static CombatEngineAPI combatEngine = null;
    private static float combatBeginTime = -1f;
    private static CombatFleetManagerAPI playerFleetManager = null;
    private static CombatTaskManagerAPI playerTaskManager = null;
    private static WeakHashMap<AssignmentInfo, Boolean> managedTasks = new WeakHashMap<>();

    public static class Tri<T, U, V> {
        public final T first;
        public final U second;
        public final V third;

        
        public Tri(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
        
        @Override
        public String toString() {
            return "(" + first + ", " + second + "," + third + ")";
        }
    }

	@Override
	public void init(CombatEngineAPI engine) {
		LOGGER.info("AutomatedEscortCombatPlugin initialized. Time: " + engine.getTotalElapsedTime(true));
        combatEngine = engine;
        playerFleetManager = combatEngine.getFleetManager(FleetSide.PLAYER);
        playerTaskManager = playerFleetManager.getTaskManager(false);
        combatBeginTime = -1f;
	}

	@Override
	public void advance(float amount, java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {
        if (combatEngine.isCombatOver()) {
            // LOGGER.info("Combat over, resetting state.");
            managedTasks.clear();
            combatBeginTime = -1;
            return;
        }

        if (playerTaskManager.isFullAssault() || playerTaskManager.isInFullRetreat()) {
            return;
        }

        updateTimer.advance(amount);
        if (!updateTimer.intervalElapsed()) {
            return;
        }

        // 护卫任务管理, 全部将权重置为0, 阻止系统自动分配
        managerTasks();

        var deployedMembers = playerFleetManager.getDeployedCopyDFM();
        if (deployedMembers.isEmpty()) {
            combatBeginTime = -1f;
            return;
        } else {
            if (combatBeginTime < 0f) {
                combatBeginTime = combatEngine.getTotalElapsedTime(false);
                LOGGER.info("Combat begin time recorded: " + combatBeginTime);
            }
        }

		var currentTime = combatEngine.getTotalElapsedTime(false);
        if (combatBeginTime > 0f && currentTime - combatBeginTime < 10f) {
            // 战斗开始10秒后才进行自动分配的处理
            return;
        }
        
        // 执行自动护卫分配
        automatedAssignment();
	}

    private void managerTasks() {
        var currentTasks = playerTaskManager.getAllAssignments();
        for (var task : currentTasks) {
            // 重置任务权重为0
            if (task.getType() == CombatAssignmentType.HEAVY_ESCORT ||
                task.getType() == CombatAssignmentType.MEDIUM_ESCORT ||
                task.getType() == CombatAssignmentType.LIGHT_ESCORT) {
                if (!managedTasks.containsKey(task)) {
                    playerTaskManager.setAssignmentWeight(task, 0);
                    managedTasks.put(task, true);
                    // LOGGER.info("Managed Task Type: " + task.getType() + ", Weight set to 0");
                }
            }
        }
    }

    private void automatedAssignment() {
        List<Tri<DeployedFleetMemberAPI, Integer, Integer>> needEscorts = new ArrayList<>();
        List<Tri<DeployedFleetMemberAPI, Integer, Integer>> canEscorts = new ArrayList<>();
        var deployedMembers = playerFleetManager.getDeployedCopyDFM();
        var playerShip = Global.getCombatEngine().getPlayerShip();
        
        // 收集需要护卫和可以护卫的舰船
        for (var member : deployedMembers) {
            var ship = member.getShip();
            var leaderTeam = (String)ship.getCustomData().getOrDefault(Constant.LEADER_ESCORT_TEAM, "");
            var memberTeam = (String)ship.getCustomData().getOrDefault(Constant.MEMBER_ESCORT_TEAM, "");
            
            if (!leaderTeam.isEmpty()) {
                // 是队长, 检查所需分数
                var desiredEscortScore = (int)ship.getCustomData().getOrDefault(Constant.LEADER_ESCORT_SCORE, 0);
                if (desiredEscortScore == 0) {
                    // 没有设置分数, 根据舰船尺寸设定默认分数
                    var shipSize = ship.getHullSize();
                    switch (shipSize) {
                        case FRIGATE:
                            desiredEscortScore = 1; // 护卫
                            break;
                        case DESTROYER:
                            desiredEscortScore = 1;
                            break;
                        case CRUISER:
                            desiredEscortScore = 2; // 中型护卫
                            break;
                        case CAPITAL_SHIP:
                            desiredEscortScore = 4; // 主力舰
                            break;
                        default:
                            desiredEscortScore = -1; // 其他舰种无效
                    }
                    ship.setCustomData(Constant.LEADER_ESCORT_SCORE, desiredEscortScore);
                }
                if (desiredEscortScore < 0) {
                    continue; // 不应该到这
                }
                
                var currentEscortScore = 0;
                var currentAssignment = playerTaskManager.getAssignmentInfoForTarget(member);
                if (currentAssignment != null &&
                    (currentAssignment.getType() == CombatAssignmentType.HEAVY_ESCORT ||
                     currentAssignment.getType() == CombatAssignmentType.MEDIUM_ESCORT ||
                     currentAssignment.getType() == CombatAssignmentType.LIGHT_ESCORT)) {
                    // 已经有护卫任务了, 统计已有的护卫分
                    var currentAssignMembers = currentAssignment.getAssignedMembers();
                    for (var m : currentAssignMembers) {
                        var mShip = m.getShip();
                        var mScore = (int)mShip.getCustomData().getOrDefault(Constant.MEMBER_ESCORT_SCORE, 0);
                        var mTeam = (String)mShip.getCustomData().getOrDefault(Constant.MEMBER_ESCORT_TEAM, "");
                        if (mTeam.equals(leaderTeam)) {
                            currentEscortScore += mScore;
                        }
                    }
                }
                var needEscortScore = desiredEscortScore - currentEscortScore;
                if (needEscortScore > 0) {
                    var priority = needEscortScore;
                    if(currentEscortScore == 0) {
                        priority = 10; // 一个护卫都没有的优先级最高
                    }
                    needEscorts.add(new Tri<>(member, needEscortScore, priority));
                }
            } else if (!memberTeam.isEmpty()) {
                // 是队员, 检查可用分数
                var escortScore = (int)ship.getCustomData().getOrDefault(Constant.MEMBER_ESCORT_SCORE, 0);
                if (escortScore > 0) {
                    // 检查是否可用, 不可以是玩家操作, 当前没有其他护卫任务
                    if (ship == playerShip) {
                        continue;
                    }
                    var currentAssignment = playerTaskManager.getAssignmentFor(ship);
                    if (currentAssignment != null &&
                        currentAssignment.getType() != CombatAssignmentType.CAPTURE &&
                        currentAssignment.getType() != CombatAssignmentType.CONTROL &&
                        currentAssignment.getType() != CombatAssignmentType.SEARCH_AND_DESTROY) {
                        continue;
                    }
                    canEscorts.add(new Tri<>(member, escortScore, 0));
                }
            }
        }
        
        if (canEscorts.isEmpty() || needEscorts.isEmpty()) {
            return;
        }

        // 排序：需要护卫的按缺口从大到小排序（按优先级排序）
        needEscorts.sort((a, b) -> Integer.compare(b.third, a.third));
        
        // 排序：可以护卫的按分数从大到小排序（优先分配高分护卫）
        canEscorts.sort((a, b) -> Integer.compare(b.second, a.second));

        // LOGGER.info("Found " + needEscorts.size() + " ships need escort, " + canEscorts.size() + " ships can escort");
        
        // 处理护卫分配 - 每次循环只为每艘需要护卫的船分配一个最佳候选
        for (Tri<DeployedFleetMemberAPI, Integer, Integer> needPair : needEscorts) {
            int requiredScore = needPair.second;
            DeployedFleetMemberAPI leader = needPair.first;
            var leaderShip = leader.getShip();
            var leaderTeam = (String)leaderShip.getCustomData().getOrDefault(Constant.LEADER_ESCORT_TEAM, "");
            
            LOGGER.info("Processing leader: " + leaderShip.getHullSpec().getHullName() + 
                       " requiring escort score: " + requiredScore + " team: " + leaderTeam);
            
            // 寻找最佳护卫候选
            DeployedFleetMemberAPI bestEscort = null;
            int bestScore = 0;
            float minDistance = Float.MAX_VALUE;
            int bestIndex = -1;
            
            for (int i = 0; i < canEscorts.size(); i++) {
                Tri<DeployedFleetMemberAPI, Integer, Integer> escortPair = canEscorts.get(i);
                int escortScore = escortPair.second;
                DeployedFleetMemberAPI escort = escortPair.first;
                var escortShip = escort.getShip();
                var escortTeam = (String)escortShip.getCustomData().getOrDefault(Constant.MEMBER_ESCORT_TEAM, "");
                
                // 检查队伍是否相同
                if (!escortTeam.equals(leaderTeam)) {
                    continue;
                }
                
                // 检查分数是否合适（不超过所需分数）
                if (escortScore > requiredScore) {
                    continue;
                }
                
                // 计算距离
                float distance = Misc.getDistanceSq(escortShip.getLocation(), leaderShip.getLocation());
                
                // 选择最佳候选：优先高分数，分数相同时选距离最近
                if (escortScore > bestScore || 
                    (escortScore == bestScore && distance < minDistance)) {
                    bestEscort = escort;
                    bestScore = escortScore;
                    minDistance = distance;
                    bestIndex = i;
                }
            }
            
            // 如果找到合适的护卫，进行分配
            if (bestEscort != null) {
                var escortShip = bestEscort.getShip();
                
                // 从候选列表中移除
                canEscorts.remove(bestIndex);
                
                LOGGER.info("Assigned escort: " + escortShip.getHullSpec().getHullName() + 
                           " with score: " + bestScore + " to leader: " + leaderShip.getHullSpec().getHullName());
                
                // 获取或创建护卫任务
                var currentAssignmentInfo = playerTaskManager.getAssignmentInfoForTarget(leader);
                
                // 确定护卫类型（基于护卫舰的大小）
                var desireType = CombatAssignmentType.LIGHT_ESCORT;
                if (escortShip.getHullSize() == com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER) {
                    desireType = CombatAssignmentType.MEDIUM_ESCORT;
                } else if (escortShip.getHullSize() == com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER || 
                          escortShip.getHullSize() == com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP) {
                    desireType = CombatAssignmentType.HEAVY_ESCORT;
                }
                
                // 如果没有任务或任务类型需要调整，创建新任务
                if (currentAssignmentInfo == null) {
                    currentAssignmentInfo = playerTaskManager.createAssignment(desireType, leader, false);
                    LOGGER.info("Created new assignment: " + currentAssignmentInfo.getType());
                } else if (currentAssignmentInfo.getType() != desireType) {
                    // 如果现有护卫队伍中已有更大的舰船，保持现有类型
                    boolean hasLargerShip = false;
                    for (var member : currentAssignmentInfo.getAssignedMembers()) {
                        if (member.getShip().getHullSize().ordinal() >= escortShip.getHullSize().ordinal()) {
                            hasLargerShip = true;
                            break;
                        }
                    }
                    if (!hasLargerShip) {
                        // 升级任务类型
                        playerTaskManager.removeAssignment(currentAssignmentInfo);
                        currentAssignmentInfo = playerTaskManager.createAssignment(desireType, leader, false);
                        LOGGER.info("Upgraded assignment to: " + currentAssignmentInfo.getType());
                    }
                }
                
                // 设置权重为0并添加到管理列表
                playerTaskManager.setAssignmentWeight(currentAssignmentInfo, 0);
                managedTasks.put(currentAssignmentInfo, true);
                
                // 分配护卫舰船
                playerTaskManager.giveAssignment(bestEscort, currentAssignmentInfo, false);
                LOGGER.info("Gave assignment to escort: " + escortShip.getHullSpec().getHullName());
                var bestEscortName = CombatLog.getShipName(escortShip);
                var leaderName = CombatLog.getShipName(leaderShip);
                Object[] args = new Object[] {
                    bestEscort,
                    CombatLog.FRIEND_COLOR, bestEscortName,
                    CombatLog.TEXT_COLOR, ": ",
                    CombatLog.HIGHLIGHT_COLOR, Local.getString(Constant.AUTOMATED_ESCORT_MSG),
                    leader,
                    CombatLog.FRIEND_COLOR, leaderName
                };
                Global.getCombatEngine().getCombatUI().addMessage(1, args);
            }
        }
    }

	@Override
	public void renderInWorldCoords(com.fs.starfarer.api.combat.ViewportAPI viewport) {
		return;
	}

	@Override
	public void renderInUICoords(com.fs.starfarer.api.combat.ViewportAPI viewport) {
		return;
	}

	@Override
	public void processInputPreCoreControls(float amount, java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {
		return;
	}

}
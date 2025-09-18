package data.hullmods;
import java.util.HashSet;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.mission.FleetSide;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;



public class SupportUnit extends com.fs.starfarer.api.combat.BaseHullMod {
    private record AssignmentKey(ShipAPI ship, AssignmentTargetAPI target, CombatAssignmentType assignmentType) {}
    private static final Logger LOGGER = Global.getLogger(SupportUnit.class);
    private final IntervalUtil timer = new IntervalUtil(0.5f, 1f);
    private HashSet<AssignmentKey> generateAssignments = new HashSet<>();

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship.getOwner() != 0) {
            // 只对玩家舰队的船生效
            return;
        }

        timer.advance(amount);
        if (!timer.intervalElapsed()) {
            return;
        }

        if (Global.getCombatEngine().isCombatOver()) {
            generateAssignments.clear(); // 船插类仅在游戏启动时实例化一次, 因此战斗结束后就要清理set
            return;
        }

        if (!canAssign(ship)) {
            return;
        }

        if (isInDanger(ship)) {
            var newRallyPoint = findRallyPoint(ship);
            if (newRallyPoint != null) {
                var currentRallyPoint = getCurrentRallyPoint(ship);
                if(currentRallyPoint != null && MathUtils.getDistance(currentRallyPoint, newRallyPoint) < 300f) {
                    return;
                }
                makeRallyPointAssignment(ship, newRallyPoint);
                LOGGER.info("SupportUnit: New rally point assigned for ship " + ship.getName() + " at " + newRallyPoint);
            } else {
                LOGGER.warn("SupportUnit: No rally point found for ship " + ship.getName());
                return;
            }
        } else {
            // 不在危险中, 清除当前的指派
            clearCurrentAssignment(ship);
        }
    }

    private boolean canAssign(ShipAPI ship) {
        var fleetManager = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        var taskManager =  fleetManager.getTaskManager(false);
        var currentAssginmentInfo = taskManager.getAssignmentFor(ship);
        if (currentAssginmentInfo != null) {
            if (currentAssginmentInfo.getType() == CombatAssignmentType.RALLY_TASK_FORCE) {
                var target = currentAssginmentInfo.getTarget();
                var key = new AssignmentKey(ship, target, currentAssginmentInfo.getType());
                if (generateAssignments.contains(key)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                if (currentAssginmentInfo.getType() != CombatAssignmentType.SEARCH_AND_DESTROY &&
                    currentAssginmentInfo.getType() != CombatAssignmentType.CAPTURE &&
                    currentAssginmentInfo.getType() != CombatAssignmentType.CONTROL) {
                    // 不是这三类任务也不继续
                    return false;
                }
            }
        }
        return true;
    }

    private Vector2f getCurrentRallyPoint(ShipAPI ship) {
        var fleetManager = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        var taskManager =  fleetManager.getTaskManager(false);
        var currentAssginmentInfo = taskManager.getAssignmentFor(ship);
        if (currentAssginmentInfo != null && currentAssginmentInfo.getType() == CombatAssignmentType.RALLY_TASK_FORCE) {
            var target = currentAssginmentInfo.getTarget();
            return target.getLocation();
        }
        return null;
    }

    private boolean isInDanger(ShipAPI ship) {
        float dangerRadius = 2000f;
        float extremeDangerRadius = 1500f;
        var nearbyEnemies = AIUtils.getNearbyEnemies(ship, dangerRadius);
        for (var enemy : nearbyEnemies) {
            if(!enemy.isAlive() || enemy.isFighter()) {
                continue;
            }
            var distance = MathUtils.getDistance(ship, enemy);
            if (distance > extremeDangerRadius) {
                continue;
            }
            if (distance > extremeDangerRadius) {
                // 这种情况下, 只有在成为敌人目标时才判断为危险
                if (enemy.getShipTarget() == ship) {
                    return true;
                }
            }
            if (distance <= extremeDangerRadius) {
                return true;
            }
        }
        return false;
    }

    private void clearCurrentAssignment(ShipAPI ship) {
        var fleetManager = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        var taskManager =  fleetManager.getTaskManager(false);
        var currentAssginmentInfo = taskManager.getAssignmentFor(ship);
        if (currentAssginmentInfo != null) {
            if (currentAssginmentInfo.getType() == CombatAssignmentType.RALLY_TASK_FORCE) {
                var target = currentAssginmentInfo.getTarget();
                var key = new AssignmentKey(ship, target, currentAssginmentInfo.getType());
                if (generateAssignments.contains(key)) {
                    // 由本插件生成的指派, 可以清除
                    taskManager.removeAssignment(currentAssginmentInfo);
                    Global.getCombatEngine().removeObject(target);
                    generateAssignments.remove(key);
                } else {
                    return; // 说明是玩家的直接指派
                }
            } 
        }
    }

    private void makeRallyPointAssignment(ShipAPI ship, Vector2f rallyPoint) {
        // 先清空当前的指派
        var fleetManager = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        var taskManager =  fleetManager.getTaskManager(false);
        var currentAssginmentInfo = taskManager.getAssignmentFor(ship);
        if (currentAssginmentInfo != null) {
            if (currentAssginmentInfo.getType() == CombatAssignmentType.RALLY_TASK_FORCE) {
                var target = currentAssginmentInfo.getTarget();
                var key = new AssignmentKey(ship, target, currentAssginmentInfo.getType());
                if (generateAssignments.contains(key)) {
                    // 由本插件生成的指派, 可以清除
                    taskManager.removeAssignment(currentAssginmentInfo);
                    Global.getCombatEngine().removeObject(target);
                    generateAssignments.remove(key);
                } else {
                    return; // 说明是玩家的直接指派
                }
            } else {
                if (currentAssginmentInfo.getType() != CombatAssignmentType.SEARCH_AND_DESTROY &&
                    currentAssginmentInfo.getType() != CombatAssignmentType.CAPTURE &&
                    currentAssginmentInfo.getType() != CombatAssignmentType.CONTROL) {
                    // 不是这三类任务也不继续
                    return;
                }
            }
        }

        // 创建一个新的目标点实体
        var wayPoint = fleetManager.createWaypoint(rallyPoint, true);
        var assignment = taskManager.createAssignment(CombatAssignmentType.RALLY_TASK_FORCE, wayPoint, false);
        taskManager.setAssignmentWeight(assignment, 0);
        var dfm = fleetManager.getDeployedFleetMember(ship);
        taskManager.giveAssignment(dfm, assignment, false);
        var key = new AssignmentKey(ship, wayPoint, CombatAssignmentType.RALLY_TASK_FORCE);
        generateAssignments.add(key);
    }

    private Vector2f findRallyPoint(ShipAPI ship) {
        var allyFleet = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER).getDeployedCopyDFM();
        
        // 第一步：找出最合适的友军
        DeployedFleetMemberAPI bestMatchAlly = null;
        float bestScore = Float.MAX_VALUE;
        
        for (var allyMember : allyFleet) {
            if (allyMember == null || allyMember.getShip() == null) {
                continue;
            }
            var allyShip = allyMember.getShip();
            if (!allyShip.isAlive() || allyShip == ship) {
                continue;
            }

            // 计算舰船尺寸乘数
            float sizeMultiplier = 1f;
            switch (allyShip.getHullSize()) {
                case FRIGATE:
                    sizeMultiplier = 1f;
                    break;
                case DESTROYER:
                    sizeMultiplier = 2f;
                    break;
                case CRUISER:
                    sizeMultiplier = 4f;
                    break;
                case CAPITAL_SHIP:
                    sizeMultiplier = 8f;
                    break;
                case FIGHTER:
                    sizeMultiplier = 0.00001f; // 忽略战机
                    break;
                case DEFAULT:
                default:
                    sizeMultiplier = 1f;
                    break;
            }
            
            // 计算距离
            float distance = MathUtils.getDistance(ship, allyShip);
            
            // 计算得分
            float score = distance / sizeMultiplier;
            
            if (score < bestScore) { // 分数越低越好(距离越近, 舰船尺寸越大越好)
                bestScore = score;
                bestMatchAlly = allyMember;
            }
        }
        
        if (bestMatchAlly == null) {
            return null; // 没有找到合适的友军
        }
        
        var allyShip = bestMatchAlly.getShip();
        
        // 第二步：找到该友军附近威胁最大的敌军单位
        ShipAPI mostThreateningEnemy = null;
        ShipAPI.HullSize largestEnemySize = null;
        float closestDistance = Float.MAX_VALUE;
        
        var nearbyEnemies = AIUtils.getNearbyEnemies(allyShip, 3000f);
        for (var enemy : nearbyEnemies) {
            if (!enemy.isAlive() || enemy.isFighter()) {
                continue;
            }
            
            // 首先按舰船尺寸排序，然后按距离
            if (largestEnemySize == null || enemy.getHullSize().ordinal() > largestEnemySize.ordinal()) {
                largestEnemySize = enemy.getHullSize();
                mostThreateningEnemy = enemy;
                closestDistance = MathUtils.getDistance(allyShip, enemy);
            } else if (enemy.getHullSize() == largestEnemySize) {
                // 相同尺寸，取最近的
                float distance = MathUtils.getDistance(allyShip, enemy);
                if (distance < closestDistance) {
                    mostThreateningEnemy = enemy;
                    closestDistance = distance;
                }
            }
        }
        
        if (mostThreateningEnemy == null) {
            // 没有找到威胁敌军，返回友军位置作为集合点
            return new Vector2f(allyShip.getLocation());
        }
        
        // 第三步：计算集合点位置
        // 以敌军和友军位置做连线，友军位置后方500的位置为集合点
        Vector2f enemyPos = mostThreateningEnemy.getLocation();
        Vector2f allyPos = allyShip.getLocation();
        
        // 计算从敌军到友军的方向向量
        Vector2f direction = Vector2f.sub(allyPos, enemyPos, null);
        direction.normalise();
        
        // 在友军后方300单位处设置集合点
        Vector2f rallyPoint = new Vector2f(allyPos);
        rallyPoint.x += direction.x * 300f;
        rallyPoint.y += direction.y * 300f;
        
        return rallyPoint;
    }
}

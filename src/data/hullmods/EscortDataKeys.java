package data.hullmods;

public enum EscortDataKeys {
    LEADER_LAST_UPDATE("escort_leader_last_update"),
    LEADER_ESCORT_SCORE("escort_leader_escort_score"),
    LEADER_CURRENT_ESCORT_SCORE("escort_leader_current_escort_score"),
    LEADER_CURRENT_ASSIGNMENT_INFO("escort_leader_current_assignment_info"),
    LEADER_CANCLE_CUSTOM_ESCORT("escort_leader_cancel_custom_escort"),
    LEADER_FIRST_DEPLOY_TIME("escort_leader_first_deploy_time"),
    LEADER_ACQUIRE_PRIORITY("escort_leader_acquire_priority"),
    MEMBER_ESCORT_SCORE("escort_member_escort_score"),
    ESCORT_TEAM("escort_team"),
    MSG_STRING_KEY("AutomatedEscort"),
    MSG_TYPE_RESTRICT("reason_not_applicable_type_restrict"),
    MSG_NOT_LEADER("reason_not_applicable_level_not_leader"),
    MSG_NOT_APPLICABLE_ON_CAPITAL_SHIP("reason_not_applicable_on_capital_ship");

    private final String value;

    EscortDataKeys(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

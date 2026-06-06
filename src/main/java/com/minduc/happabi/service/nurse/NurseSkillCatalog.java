package com.minduc.happabi.service.nurse;

import com.minduc.happabi.enums.NurseSkill;

public final class NurseSkillCatalog {

    private NurseSkillCatalog() {
    }

    public static String label(NurseSkill skill) {
        return switch (skill) {
            case POSTPARTUM_RECOVERY_MASSAGE -> "Massage phục hồi sau sinh";
            case PRENATAL_RELAXATION_MASSAGE -> "Massage thư giãn mẹ bầu";
            case FOOT_PAIN_RELIEF_MASSAGE -> "Massage giảm đau vai gáy";
            case POSTPARTUM_BACK_SHOULDER_NECK_MASSAGE -> "Massage bụng giảm mỡ sau sinh";
            case LACTATION_STIMULATION -> "Kích sữa";
            case BLOCKED_MILK_DUCT_SUPPORT -> "Thông tắc tia sữa";
            case BREAST_CARE -> "Chăm sóc tuyến vú";
            case BREASTFEEDING_POSITION_GUIDANCE -> "Hướng dẫn cho bé bú đúng tư thế";
            case POSTPARTUM_HEALTH_MONITORING -> "Theo dõi sức khỏe mẹ sau sinh";
            case NEWBORN_BATHING -> "Tắm bé sơ sinh";
            case NEWBORN_BASIC_CARE -> "Vệ sinh rốn cho bé";
            case NEWBORN_HEALTH_MONITORING -> "Theo dõi sức khỏe bé sơ sinh";
            case NEWBORN_SKIN_CARE -> "Chăm sóc da cho bé";
            case HOME_NEWBORN_CARE_GUIDANCE -> "Hướng dẫn chăm sóc bé tại nhà";
            case NEWBORN_WARNING_SIGN_RECOGNITION -> "Nhận biết dấu hiệu bất thường ở trẻ sơ sinh";
            case PARENT_COMMUNICATION -> "Giao tiếp với phụ huynh";
            case MOTHER_BABY_CONSULTING -> "Tư vấn chăm sóc mẹ & bé";
            case SITUATION_HANDLING -> "Xử lý tình huống";
            case CUSTOMER_CARE -> "Chăm sóc khách hàng";
            case SCHEDULE_MANAGEMENT -> "Quản lý lịch hẹn";
        };
    }

    public static String groupName(NurseSkill skill) {
        return switch (skill) {
            case POSTPARTUM_RECOVERY_MASSAGE,
                 PRENATAL_RELAXATION_MASSAGE,
                 FOOT_PAIN_RELIEF_MASSAGE,
                 POSTPARTUM_BACK_SHOULDER_NECK_MASSAGE,
                 LACTATION_STIMULATION,
                 BLOCKED_MILK_DUCT_SUPPORT,
                 BREAST_CARE,
                 BREASTFEEDING_POSITION_GUIDANCE,
                 POSTPARTUM_HEALTH_MONITORING -> "Chăm sóc mẹ sau sinh";
            case NEWBORN_BATHING,
                 NEWBORN_BASIC_CARE,
                 NEWBORN_HEALTH_MONITORING,
                 NEWBORN_SKIN_CARE,
                 HOME_NEWBORN_CARE_GUIDANCE,
                 NEWBORN_WARNING_SIGN_RECOGNITION -> "Chăm sóc bé sơ sinh";
            case PARENT_COMMUNICATION,
                 MOTHER_BABY_CONSULTING,
                 SITUATION_HANDLING,
                 CUSTOMER_CARE,
                 SCHEDULE_MANAGEMENT -> "Kỹ năng mềm";
        };
    }
}

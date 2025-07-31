package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.AlipayUser
import fansirsqi.xposed.sesame.extensions.JSONExtensions.toJSONArray
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import fansirsqi.xposed.sesame.task.antFarm.AntFarm.AnimalInteractStatus
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

data object AntFarmFamily {
    private const val TAG = "小鸡家庭"

    /** 家庭ID */
    private var groupId: String = ""

    /** 家庭名称 */
    private var groupName: String = ""

    /** 家庭成员对象 */
    private var familyAnimals: JSONArray = JSONArray()

    /** 家庭成员列表 */
    private var familyUserIds: MutableList<String> = mutableListOf()

    /** 互动功能列表 */
    private var familyInteractActions: JSONArray = JSONArray()

    /** 美食配置对象 */
    private var eatTogetherConfig: JSONObject = JSONObject()

    fun run(familyOptions: SelectModelField, notInviteList: SelectModelField) {
        try {
            enterFamily(familyOptions, notInviteList)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e.message, e)
        }
    }

    /** 进入家庭并执行任务 */
    fun enterFamily(familyOptions: SelectModelField, notInviteList: SelectModelField) {
        try {
            val enterRes = JSONObject(AntFarmRpcCall.enterFamily())
            if (!ResChecker.checkRes(TAG, enterRes)) return

            groupId = enterRes.optString("groupId")
            groupName = enterRes.optString("groupName")
            val familyAwardNum = enterRes.optInt("familyAwardNum", 0)
            val familySignTips = enterRes.optBoolean("familySignTips", false)
            val assignFamilyMemberInfo = enterRes.optJSONObject("assignFamilyMemberInfo")
            familyAnimals = enterRes.optJSONArray("animals") ?: JSONArray()
            familyUserIds = (0 until familyAnimals.length())
                .map { familyAnimals.getJSONObject(it).getString("userId") }
                .toMutableList()
            familyInteractActions = enterRes.optJSONArray("familyInteractActions") ?: JSONArray()
            eatTogetherConfig = enterRes.optJSONObject("eatTogetherConfig") ?: JSONObject()

            // 一、签到相关任务
            if (familyOptions.value.contains("familySign") && familySignTips) {
                familySign()
            }

            if (assignFamilyMemberInfo != null
                && familyOptions.value.contains("assignRights")
                && assignFamilyMemberInfo.optJSONObject("assignRights")?.optString("status") != "USED"
            ) {
                if (assignFamilyMemberInfo.optJSONObject("assignRights")?.optString("assignRightsOwner") == UserMap.currentUid) {
                    assignFamilyMember(assignFamilyMemberInfo, familyUserIds)
                } else {
                    Log.record("家庭任务🏡[使用顶梁柱特权] 不是家里的顶梁柱！")
                    familyOptions.value.remove("assignRights")
                }
            }

            if (familyOptions.value.contains("familyClaimReward") && familyAwardNum > 0) {
                familyClaimRewardList()
            }

            // 二、互动类任务
            if (familyOptions.value.contains("feedFamilyAnimal")) {
                familyFeedFriendAnimal(familyAnimals)
            }
            if (familyOptions.value.contains("eatTogetherConfig")) {
                familyEatTogether(eatTogetherConfig, familyInteractActions, familyUserIds)
            }
            if (familyOptions.value.contains("deliverMsgSend")) {
                deliverMsgSend(familyUserIds)
            }
            if (familyOptions.value.contains("shareToFriends")) {
                familyShareToFriends(familyUserIds, notInviteList)
            }

        } catch (e: Exception) {
            Log.printStackTrace(TAG, e.message, e)
        }
    }

    /** 家庭签到 */
    fun familySign() {
        try {
            if (Status.hasFlagToday("farmfamily::dailySign")) return
            val res = JSONObject(AntFarmRpcCall.familyReceiveFarmTaskAward("FAMILY_SIGN_TASK"))
            if (ResChecker.checkRes(TAG, res)) {
                Log.farm("家庭任务🏡每日签到")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e.message, e)
        }
    }

    /** 领取家庭奖励 */
    fun familyClaimRewardList() {
        try {
            val jo = JSONObject(AntFarmRpcCall.familyAwardList())
            if (!ResChecker.checkRes(TAG, jo)) return

            val ja = jo.optJSONArray("familyAwardRecordList") ?: return
            for (i in 0 until ja.length()) {
                val award = ja.getJSONObject(i)
                if (award.optBoolean("expired")
                    || award.optBoolean("received", true)
                    || award.has("linkUrl")
                    || (award.has("operability") && !award.getBoolean("operability"))
                ) continue

                val rightId = award.optString("rightId")
                val awardName = award.optString("awardName")
                val count = award.optInt("count", 1)
                val receiveRes = JSONObject(AntFarmRpcCall.receiveFamilyAward(rightId))
                if (ResChecker.checkRes(TAG, receiveRes)) {
                    Log.farm("家庭奖励🏆: $awardName x $count")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "家庭领取奖励", t)
        }
    }

    /** 使用顶梁柱特权 */
    fun assignFamilyMember(jsonObject: JSONObject, userIds: MutableList<String>) {
        try {
            userIds.remove(UserMap.currentUid)
            if (userIds.isEmpty()) return

            val beAssignUser = userIds.random()
            val assignConfigList = jsonObject.optJSONArray("assignConfigList") ?: JSONArray()
            if (assignConfigList.length() == 0) return

            val assignConfig = assignConfigList.getJSONObject(RandomUtil.nextInt(0, assignConfigList.length() - 1))
            val jo = JSONObject(AntFarmRpcCall.assignFamilyMember(assignConfig.optString("assignAction"), beAssignUser))
            if (ResChecker.checkRes(TAG, jo)) {
                Log.farm("家庭任务🏡[使用顶梁柱特权] ${assignConfig.optString("assignDesc")}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    /** 帮好友喂小鸡 */
    fun familyFeedFriendAnimal(animals: JSONArray) {
        try {
            for (i in 0 until animals.length()) {
                val animal = animals.getJSONObject(i)
                val animalStatusVo = animal.optJSONObject("animalStatusVO") ?: continue
                val interactStatus = animalStatusVo.optString("animalInteractStatus")
                val feedStatus = animalStatusVo.optString("animalFeedStatus")
                if (interactStatus == AnimalInteractStatus.HOME.name && feedStatus == AnimalFeedStatus.HUNGRY.name) {
                    val groupId = animal.optString("groupId")
                    val farmId = animal.optString("farmId")
                    val userId = animal.optString("userId")

                    if (!UserMap.getUserIdSet().contains(userId)) {
                        Log.error(TAG, "$userId 不是你的好友！ 跳过家庭喂食")
                        continue
                    }
                    if (Status.hasFlagToday("farm::feedFriendLimit")) {
                        Log.runtime("今日喂鸡次数已达上限🥣 家庭喂")
                        return
                    }
                    val jo = JSONObject(AntFarmRpcCall.feedFriendAnimal(farmId, groupId))
                    if (ResChecker.checkRes(TAG, jo)) {
                        Log.farm("家庭任务🏠帮喂好友🥣[${UserMap.getMaskName(userId)}]的小鸡180g #剩余${jo.optInt("foodStock")}g")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "familyFeedFriendAnimal err:")
            Log.printStackTrace(TAG, t)
        }
    }

    /** 请客吃美食 */
    private fun familyEatTogether(eatTogetherConfig: JSONObject, familyInteractActions: JSONArray, familyUserIds: MutableList<String>) {
        try {
            val periodItemList = eatTogetherConfig.optJSONArray("periodItemList") ?: JSONArray()
            if (periodItemList.length() == 0) {
                Log.error(TAG, "美食不足,无法请客,请检查小鸡厨房")
                return
            }
            // 判断是否正在吃饭
            for (i in 0 until familyInteractActions.length()) {
                val familyInteractAction = familyInteractActions.getJSONObject(i)
                if (familyInteractAction.optString("familyInteractType") == "EatTogether") {
                    val endTime = familyInteractAction.optLong("interactEndTime", 0)
                    val gapTime = endTime - System.currentTimeMillis()
                    Log.record("正在吃..${formatDuration(gapTime)} 吃完")
                    return
                }
            }
            // 检查当前时间是否在美食时间段
            val currentTime = Calendar.getInstance()
            var periodName = ""
            var isEat = false
            for (i in 0 until periodItemList.length()) {
                val periodItem = periodItemList.getJSONObject(i)
                val startTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, periodItem.optInt("startHour"))
                    set(Calendar.MINUTE, periodItem.optInt("startMinute"))
                }
                val endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, periodItem.optInt("endHour"))
                    set(Calendar.MINUTE, periodItem.optInt("endMinute"))
                }
                if (currentTime.after(startTime) && currentTime.before(endTime)) {
                    periodName = periodItem.optString("periodName")
                    isEat = true
                    break
                }
            }
            if (!isEat) {
                Log.record("家庭任务🏠请客吃美食#当前时间不在美食时间段")
                return
            }
            if (familyUserIds.isEmpty()) {
                Log.record("家庭成员列表为空,无法请客")
                return
            }
            val recentFoods = queryRecentFarmFood(familyUserIds.size)
            if (recentFoods == null) {
                Log.record("查询最近的几份美食为空,无法请客")
                return
            }
            val jo = JSONObject(AntFarmRpcCall.familyEatTogether(groupId, familyUserIds.toJSONArray(), recentFoods))
            if (ResChecker.checkRes(TAG, jo)) {
                Log.farm("家庭任务🏠请客$periodName#消耗美食${familyUserIds.size}份")
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "familyEatTogether err:")
            Log.printStackTrace(TAG, t)
        }
    }

    /** 查询最近的几份美食 */
    fun queryRecentFarmFood(queryNum: Int): JSONArray? {
        try {
            val jo = JSONObject(AntFarmRpcCall.queryRecentFarmFood(queryNum))
            if (!ResChecker.checkRes(TAG, jo)) return null

            val cuisines = jo.optJSONArray("cuisines") ?: return null
            var count = 0
            for (i in 0 until cuisines.length()) {
                val cuisine = cuisines.getJSONObject(i)
                count += cuisine.optInt("count", 0)
            }
            return if (count >= queryNum) cuisines else null
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryRecentFarmFood err:", t)
        }
        return null
    }

   /**
 * 发送道早安
 * @param familyUserIds 家庭成员列表
 */
fun deliverMsgSend(groupId: String, familyUserIds: MutableList<String>) {
    try {
        // 时间窗口检查 6:00 - 10:00
        val now = Calendar.getInstance()
        val startTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
        }
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        if (now.before(startTime) || now.after(endTime)) {
            Log.record("当前不在道早安时间窗口，跳过任务")
            return
        }

        if (groupId.isEmpty()) {
            Log.record("未绑定家庭 groupId，跳过任务")
            return
        }

        // 移除当前用户自身
        familyUserIds.remove(UserMap.currentUid)
        if (familyUserIds.isEmpty()) {
            Log.record("家庭成员为空，跳过任务")
            return
        }

        if (Status.hasFlagToday("antFarm::deliverMsgSend")) {
            Log.record("今日已执行道早安任务")
            return
        }

        val userIds = JSONArray()
        familyUserIds.forEach { userIds.put(it) }

        // 1. familyTaskTips 检查任务状态
        val taskTipsRespStr = AntFarmRpcCall.familyTaskTips()
        val taskTipsResp = JSONObject(taskTipsRespStr)
        if (!ResChecker.checkRes("小鸡家庭", taskTipsResp)) return

        val dataTips = taskTipsResp.optJSONObject("Data") ?: run {
            Log.record("familyTaskTips响应缺少Data字段")
            return
        }
        val tipsArr = dataTips.optJSONArray("familyTaskTips")
        if (tipsArr == null || tipsArr.length() == 0) {
            Log.record("家庭任务列表为空，跳过道早安")
            return
        }

        var canSayMorning = false
        for (i in 0 until tipsArr.length()) {
            val tip = tipsArr.getJSONObject(i)
            if (tip.optString("bizKey") == "GREETING" && tip.optInt("canReceiveAwardCount", 0) > 0) {
                canSayMorning = true
                break
            }
        }
        if (!canSayMorning) {
            Log.record("道早安任务当前不可完成")
            return
        }

        // 2. deliverSubjectRecommend 获取推荐主题
        val subjectRespStr = AntFarmRpcCall.deliverSubjectRecommend(userIds)
        val subjectRespJson = JSONObject(subjectRespStr)
        if (!ResChecker.checkRes("小鸡家庭", subjectRespJson)) return
        val dataSubject = subjectRespJson.optJSONObject("Data") ?: run {
            Log.record("deliverSubjectRecommend响应缺少Data字段")
            return
        }

        val traceId = dataSubject.optString("ariverRpcTraceId")
        val contentFallback = dataSubject.optString("content") // 有时deliverSubjectRecommend没content字段可以做个兜底
        val deliverIdFallback = dataSubject.optString("deliverId")

        if (traceId.isEmpty()) {
            Log.record("推荐主题返回traceId为空")
            return
        }

        // 3. deliverContentExpand 生成问候语内容
        val expandRespStr = AntFarmRpcCall.deliverContentExpand(userIds, traceId)
        val expandRespJson = JSONObject(expandRespStr)
        if (!ResChecker.checkRes("小鸡家庭", expandRespJson)) return
        val dataExpand = expandRespJson.optJSONObject("Data") ?: run {
            Log.record("deliverContentExpand响应缺少Data字段")
            return
        }

        val content = dataExpand.optString("content").ifEmpty { contentFallback }
        val deliverId = dataExpand.optString("deliverId").ifEmpty { deliverIdFallback }

        if (content.isEmpty() || deliverId.isEmpty()) {
            Log.record("传话内容或deliverId为空")
            return
        }

        // 4. queryExpandContent 再确认内容（可选）
        val queryRespStr = AntFarmRpcCall.queryExpandContent(deliverId)
        val queryRespJson = JSONObject(queryRespStr)
        if (!ResChecker.checkRes("小鸡家庭", queryRespJson)) return
        val dataQuery = queryRespJson.optJSONObject("Data") ?: run {
            Log.record("queryExpandContent响应缺少Data字段")
            return
        }

        val finalContent = dataQuery.optString("content", content)

        // 5. 发送道早安消息
        val sendRespStr = AntFarmRpcCall.deliverMsgSend(groupId, userIds, finalContent, deliverId)
        val sendRespJson = JSONObject(sendRespStr)
        if (ResChecker.checkRes("小鸡家庭", sendRespJson)) {
            Log.farm("小鸡家庭", "家庭任务🏠道早安：$finalContent 🌈")
            Status.setFlagToday("antFarm::deliverMsgSend")

            // 6. 同步家庭状态
            val syncRespStr = AntFarmRpcCall.syncFamilyStatus(groupId, userIds)
            val syncRespJson = JSONObject(syncRespStr)
            if (ResChecker.checkRes("小鸡家庭", syncRespJson)) {
                Log.record("同步家庭状态成功")
            } else {
                Log.record("同步家庭状态失败")
            }
        } else {
            Log.record("deliverMsgSend接口调用失败")
        }
    } catch (t: Throwable) {
        Log.printStackTrace("小鸡家庭", "deliverMsgSend 执行异常：", t)
    }
}

    /** 好友分享家庭 */
    private fun familyShareToFriends(familyUserIds: MutableList<String>, notInviteList: SelectModelField) {
        try {
            if (Status.hasFlagToday("antFarm::familyShareToFriends")) return

            val familyValue: MutableSet<String?> = notInviteList.value
            val allUser: List<AlipayUser> = AlipayUser.getList()
            if (allUser.isEmpty()) {
                Log.error(TAG, "allUser is empty")
                return
            }

            val shuffledUsers = allUser.shuffled()
            val inviteList = JSONArray()

            for (u in shuffledUsers) {
                if (!familyUserIds.contains(u.id) && !familyValue.contains(u.id)) {
                    inviteList.put(u.id)
                    if (inviteList.length() >= 6) break
                }
            }
            if (inviteList.length() == 0) {
                Log.error(TAG, "没有符合分享条件的好友")
                return
            }

            Log.runtime(TAG, "inviteList: $inviteList")

            val jo = JSONObject(AntFarmRpcCall.inviteFriendVisitFamily(inviteList))
            if (ResChecker.checkRes(TAG, jo)) {
                Log.farm("家庭任务🏠分享好友")
                Status.setFlagToday("antFarm::familyShareToFriends")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "familyShareToFriends err:", t)
        }
    }

    /** 时间差格式化 */
    fun formatDuration(diffMillis: Long): String {
        val absSeconds = abs(diffMillis) / 1000

        val (value, unit) = when {
            absSeconds < 60 -> Pair(absSeconds, "秒")
            absSeconds < 3600 -> Pair(absSeconds / 60, "分钟")
            absSeconds < 86400 -> Pair(absSeconds / 3600, "小时")
            absSeconds < 2592000 -> Pair(absSeconds / 86400, "天")
            absSeconds < 31536000 -> Pair(absSeconds / 2592000, "个月")
            else -> Pair(absSeconds / 31536000, "年")
        }

        return when {
            absSeconds < 1 -> "刚刚"
            diffMillis > 0 -> "$value$unit 后"
            else -> "$value$unit 前"
        }
    }
}

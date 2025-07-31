package fansirsqi.xposed.sesame.task.antFarm; // 注意包路径匹配

import fansirsqi.xposed.sesame.util.TimeUtil;

public class AntFarmFamilyOption {
    private final String value;
    private final String name;
    private final boolean timeSensitive;
    private final String activeTime;

    // 构造方法
    public AntFarmFamilyOption(String value, String name) {
        this(value, name, false, "0000-2359");
    }

    public AntFarmFamilyOption(String value, String name, boolean timeSensitive, String activeTime) {
        this.value = value;
        this.name = name;
        this.timeSensitive = timeSensitive;
        this.activeTime = activeTime;
    }

    // Getter方法
    public String getValue() { return value; }
    public String getName() { return name; }
    public boolean isTimeSensitive() { return timeSensitive; }
    public String getActiveTime() { return activeTime; }

    // 行为方法
    public boolean isInActiveTime() {
        return !timeSensitive || TimeUtil.isInTimeRange(activeTime);
    }

    // 静态默认选项
    public static List<AntFarmFamilyOption> getDefaultOptions() {
        return Arrays.asList(
            new AntFarmFamilyOption("familySign", "每日签到", true, "0000-2359"),
            new AntFarmFamilyOption("sendMorningMsg", "道早安", true, "0600-1000"),
            // 其他选项...
        );
    }
}

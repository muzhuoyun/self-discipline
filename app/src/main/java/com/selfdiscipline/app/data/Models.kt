package com.selfdiscipline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 四大分组：三戒 / 三修 / 四德（人品与处世） */
enum class Group(val title: String) {
    JIE("三戒"),
    XIU("三修"),
    DE("四德"),
}

/**
 * 十类指标：三戒（戒淫 / 戒馋 / 戒贪）+ 三修（修养 / 修体 / 修行）
 * + 四德（孝 / 诚 / 和 / 勤），共 100 分。
 */
enum class Category(val key: String, val title: String, val group: Group) {
    JIE_YIN("jieyin", "戒淫", Group.JIE),
    JIE_CHAN("jiechan", "戒馋", Group.JIE),
    JIE_TAN("jietan", "戒贪", Group.JIE),
    XIU_YANG("xiuyang", "修养", Group.XIU),
    XIU_TI("xiuti", "修体", Group.XIU),
    XIU_XING("xiuxing", "修行", Group.XIU),
    XIAO("xiao", "孝", Group.DE),
    CHENG("cheng", "诚", Group.DE),
    HE("he", "和", Group.DE),
    QIN("qin", "勤", Group.DE);

    companion object {
        fun fromKey(key: String): Category = entries.first { it.key == key }
    }
}

/**
 * 每日记录，[date] 为 ISO 格式（yyyy-MM-dd）。
 * 戒淫是单选等级（0 / 5 / 8 / 10）；其余九类是逐项勾选，用位掩码存储：
 * 第 i 位表示该项指标是否完成。
 */
@Entity(tableName = "daily_record")
data class DailyRecord(
    @PrimaryKey val date: String,
    val jieYin: Int = 0,
    val jieChanMask: Int = 0,
    val jieTanMask: Int = 0,
    val xiuYangMask: Int = 0,
    val xiuTiMask: Int = 0,
    val xiuXingMask: Int = 0,
    val xiaoMask: Int = 0,
    val chengMask: Int = 0,
    val heMask: Int = 0,
    val qinMask: Int = 0,
    /** 是否为 60 分旧制记录（迁移时标记）；旧记录总分按 60→100 等比折算展示 */
    val legacy: Boolean = false,
)

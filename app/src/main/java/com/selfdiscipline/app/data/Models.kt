package com.selfdiscipline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 六类指标：三戒（戒淫 / 戒馋 / 戒贪）+ 三修（修养 / 修体 / 修行）。
 */
enum class Category(val key: String, val title: String, val isJie: Boolean) {
    JIE_YIN("jieyin", "戒淫", true),
    JIE_CHAN("jiechan", "戒馋", true),
    JIE_TAN("jietan", "戒贪", true),
    XIU_YANG("xiuyang", "修养", false),
    XIU_TI("xiuti", "修体", false),
    XIU_XING("xiuxing", "修行", false);

    companion object {
        fun fromKey(key: String): Category = entries.first { it.key == key }
    }
}

/**
 * 每日记录，[date] 为 ISO 格式（yyyy-MM-dd）。
 *
 * 戒淫是单选等级（0 / 5 / 8 / 10）；其余五类是逐项勾选，用位掩码存储：
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
)

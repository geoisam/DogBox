package pjs.dogbox.utils

import com.tyme.festival.SolarFestival
import com.tyme.lunar.LunarDay
import com.tyme.sixtycycle.HeavenStem
import com.tyme.solar.SolarDay
import com.tyme.solar.SolarTime
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

data class FestivalItem(
    val name: String,
    val date: LocalDate,
    val weekday: String,
    val daysLeft: Long,
)

data class LunarSnapshot(
    val yearMonth: String,
    val shiChen: String,
    val week: String,
    val day: String,
    val monthDay: String,
    val ganZhi: String,
    val jieQi: String,
    val dayYi: String,
    val dayJi: String,
    val dayChong: String,
    val pengZu: String,
    val dayXi: String,
    val dayCai: String,
    val dayFu: String,
    val festivals: List<FestivalItem>
)

object LunarUtil {
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun String.toDateString(): String {
        return try {
            val timestamp = this.toLong()
            formatter.format(Date(timestamp))
        } catch (e: Exception) {
            getDateTime()
        }
    }

    fun getYearMonthDay(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val yearSolar = timeNow.year.toString().padStart(2, '0')
        val monthSolar = timeNow.monthValue.toString().padStart(2, '0')
        val daySolar = timeNow.dayOfMonth.toString().padStart(2, '0')
        return "${yearSolar}-${monthSolar}-${daySolar}"
    }

    fun getDateTime(): String {
        val yearMonthDay = getYearMonthDay()
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val hourSolar = timeNow.hour.toString().padStart(2, '0')
        val minuteSolar = timeNow.minute.toString().padStart(2, '0')
        val secondSolar = timeNow.second.toString().padStart(2, '0')
        return "$yearMonthDay ${hourSolar}:${minuteSolar}:${secondSolar}"
    }

    fun getYearMonth(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val yearSolar = timeNow.year
        val monthSolar = timeNow.monthValue
        return "$yearSolar 年 $monthSolar 月"
    }

    fun getDay(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        return timeNow.dayOfMonth.toString()
    }

    fun getWeek(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val solarDay = SolarDay.fromYmd(timeNow.year, timeNow.monthValue, timeNow.dayOfMonth)
        return "星期${solarDay.getWeek().getName()}"
    }

    fun getMonthDay(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        return "${lunarDay.getLunarMonth().getName()}${lunarDay.getName()}"
    }

    fun getGanZhi(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val yearSixtyCycle = lunarDay.getLunarMonth().getLunarYear().getSixtyCycle()
        val monthSixtyCycle = lunarDay.getLunarMonth().getSixtyCycle()
        val daySixtyCycle = lunarDay.getSixtyCycle()
        val yearZodiac = yearSixtyCycle.getEarthBranch().getZodiac().getName()
        val monthZodiac = monthSixtyCycle.getEarthBranch().getZodiac().getName()
        val dayZodiac = daySixtyCycle.getEarthBranch().getZodiac().getName()
        return "${yearSixtyCycle.getName()}${yearZodiac}年 ${monthSixtyCycle.getName()}${monthZodiac}月 ${daySixtyCycle.getName()}${dayZodiac}日"
    }

    fun getJieQi(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val solarDay = SolarDay.fromYmd(timeNow.year, timeNow.monthValue, timeNow.dayOfMonth)
        val term = solarDay.getTerm()
        if (term != null) {
            val termSolarDay = term.getSolarDay()
            if (termSolarDay.getYear() == solarDay.getYear()
                && termSolarDay.getMonth() == solarDay.getMonth()
                && termSolarDay.getDay() == solarDay.getDay()
            ) {
                return term.getName()
            }
        }
        return ""
    }

    fun getDayYi(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val recommends = lunarDay.getRecommends()
        return if (recommends.isNotEmpty()) recommends.joinToString(" ") { it.getName() } else "无"
    }

    fun getDayJi(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val avoids = lunarDay.getAvoids()
        return if (avoids.isNotEmpty()) avoids.joinToString(" ") { it.getName() } else "无"
    }

    fun getDayChong(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val sc = lunarDay.getSixtyCycle()
        val eb = sc.getEarthBranch()
        val dayShengXiao = eb.getZodiac().getName()
        val dayChongShengXiao = eb.getOpposite().getZodiac().getName()
        val dayChongGan = HeavenStem.fromIndex(sc.getHeavenStem().getIndex() + 6).getName()
        val dayChong = eb.getOpposite().getName()
        val daySha = eb.getOminous().getName()
        return "${dayShengXiao}日冲${dayChongShengXiao}(${dayChongGan}${dayChong}) 煞${daySha}"
    }

    fun getPengZu(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val pengZu = lunarDay.getSixtyCycle().getPengZu()
        return "${pengZu.getPengZuHeavenStem().getName()}\n${
            pengZu.getPengZuEarthBranch().getName()
        }"
    }

    fun getDayXi(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val hs = lunarDay.getSixtyCycle().getHeavenStem()
        val direction = hs.getJoyDirection().getName()
        return "${direction}方(${direction})"
    }

    fun getDayCai(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val hs = lunarDay.getSixtyCycle().getHeavenStem()
        val direction = hs.getWealthDirection().getName()
        return "${direction}方(${direction})"
    }

    fun getDayFu(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val lunarDay = getLunarDay(timeNow)
        val hs = lunarDay.getSixtyCycle().getHeavenStem()
        val direction = hs.getMascotDirection().getName()
        return "${direction}方(${direction})"
    }

    fun getShiChen(): String {
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
        val solarTime = SolarTime.fromYmdHms(
            timeNow.year,
            timeNow.monthValue,
            timeNow.dayOfMonth,
            timeNow.hour,
            timeNow.minute,
            timeNow.second
        )
        val lunarHour = solarTime.getLunarHour()
        val timeZhi = lunarHour.getSixtyCycleHour().getSixtyCycle().getEarthBranch().getName()
        return "${timeZhi}时"
    }

    fun takeSnapshot(): LunarSnapshot {
        val now = ZonedDateTime.now(ZoneId.systemDefault())

        return LunarSnapshot(
            yearMonth = try {
                getYearMonth()
            } catch (_: Exception) {
                ""
            },
            shiChen = try {
                getShiChen()
            } catch (_: Exception) {
                ""
            },
            week = try {
                getWeek()
            } catch (_: Exception) {
                ""
            },
            day = try {
                getDay()
            } catch (_: Exception) {
                ""
            },
            monthDay = try {
                getMonthDay()
            } catch (_: Exception) {
                ""
            },
            ganZhi = try {
                getGanZhi()
            } catch (_: Exception) {
                ""
            },
            jieQi = try {
                getJieQi()
            } catch (_: Exception) {
                ""
            },
            dayYi = try {
                getDayYi()
            } catch (_: Exception) {
                ""
            },
            dayJi = try {
                getDayJi()
            } catch (_: Exception) {
                ""
            },
            dayChong = try {
                getDayChong()
            } catch (_: Exception) {
                ""
            },
            pengZu = try {
                getPengZu()
            } catch (_: Exception) {
                ""
            },
            dayXi = try {
                getDayXi()
            } catch (_: Exception) {
                ""
            },
            dayCai = try {
                getDayCai()
            } catch (_: Exception) {
                ""
            },
            dayFu = try {
                getDayFu()
            } catch (_: Exception) {
                ""
            },
            festivals = try {
                getNextFestivals(now)
            } catch (_: Exception) {
                emptyList()
            }
        )
    }

    private fun getLunarDay(timeNow: ZonedDateTime): LunarDay {
        val solarTime = SolarTime.fromYmdHms(
            timeNow.year,
            timeNow.monthValue,
            timeNow.dayOfMonth,
            timeNow.hour,
            timeNow.minute,
            timeNow.second
        )
        return solarTime.getLunarHour().getLunarDay()
    }

    private fun getNextFestivals(now: ZonedDateTime): List<FestivalItem> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val maxDays = 99

        var nearestJieQi: FestivalItem? = null
        var nearestFestivalDay: Long? = null
        var nearestFestivalName: String? = null

        for (i in 0 until maxDays) {
            val date = today.plusDays(i.toLong())
            val solarDay = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)

            if (nearestJieQi == null) {
                val term = solarDay.getTerm()
                if (term != null) {
                    val termSolarDay = term.getSolarDay()
                    if (termSolarDay.getYear() == solarDay.getYear()
                        && termSolarDay.getMonth() == solarDay.getMonth()
                        && termSolarDay.getDay() == solarDay.getDay()
                    ) {
                        nearestJieQi = FestivalItem(
                            name = term.getName(),
                            date = date,
                            weekday = solarDay.getWeek().getName(),
                            daysLeft = i.toLong()
                        )
                    }
                }
            }

            if (nearestFestivalDay == null) {
                val festival = SolarFestival.fromYmd(
                    solarDay.getYear(),
                    solarDay.getMonth(),
                    solarDay.getDay()
                )
                if (festival != null) {
                    nearestFestivalDay = i.toLong()
                    nearestFestivalName = festival.getName()
                }
            }

            if (nearestJieQi != null && nearestFestivalDay != null) break
        }

        val result = mutableListOf<FestivalItem>()

        nearestJieQi?.let { result.add(it) }

        nearestFestivalDay?.let { days ->
            val date = today.plusDays(days)
            val solarDay = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            nearestFestivalName?.let { name ->
                result.add(
                    FestivalItem(
                        name = name,
                        date = date,
                        weekday = solarDay.getWeek().getName(),
                        daysLeft = days
                    )
                )
            }
        }

        return result.sortedBy { it.daysLeft }
    }
}

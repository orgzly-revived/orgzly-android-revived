package com.orgzly.android.query

/**
 * Similar to [OrgInterval] with support for "none", "M" (minute),
 * "today" (0d), "tomorrow" (1d) and "yesterday" (-1d).
 **/
class QueryInterval(val u: QueryInterval.Unit, val v: Int = 0) {
    enum class Unit(val text: String) {
        NONE("none"),
        NOW("now"),
        HOUR("h"),
        DAY("d"),
        WEEK("w"),
        MONTH("m"),
        YEAR("y");

        override fun toString(): String {
            return text
        }

        companion object {
            fun from(text: String): Unit = Unit.values().first { it.text == text }
        }
    }

    public var value: Int = v
    public var unit: QueryInterval.Unit = u

    private fun setValue(str: String) {
        try {
            value = str.toInt()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Interval value '$str' couldn't be parsed as integer", e);
        }
    }

    override public fun toString(): String {
        return when {
            unit == Unit.DAY && value ==  0 -> "today"
            unit == Unit.DAY && value ==  1 -> "tomorrow"
            unit == Unit.DAY && value == -1 -> "yesterday"
            unit == Unit.NONE || unit == Unit.NOW -> unit.toString()
            else -> "$value${unit.toString()}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryInterval

        if (value != other.value) return false
        if (unit != other.unit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + unit.hashCode()
        return result
    }

    companion object {
        private val REGEX = Regex("^([-+]?\\d+)([hdwmy])$")

        fun parse(str: String): QueryInterval? = when (str.lowercase()) {
            "none", "no" -> {
                QueryInterval(Unit.NONE)
            }

            "now" -> {
                QueryInterval(Unit.NOW)
            }

            "today", "tod" -> {
                QueryInterval(Unit.DAY, 0)
            }

            "tomorrow", "tmrw", "tom" -> {
                QueryInterval(Unit.DAY, 1)
            }

            "yesterday" -> {
                QueryInterval(Unit.DAY, -1)
            }

            else -> {
                val m = REGEX.find(str)

                if (m != null) {
                    var unit = Unit.from(m.groupValues[2])
                    var interval = QueryInterval(unit)
                    interval.setValue(m.groupValues[1])
                    interval

                } else {
                    null
                }
            }
        }
    }
}

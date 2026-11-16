package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.TimeSlotJsonModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

object CumtWebScripts {
    const val KEY_BK = "cumt_bk"
    const val KEY_YJS = "cumt_yjs"

    val presetTimeSlots: List<TimeSlotJsonModel> = listOf(
        TimeSlotJsonModel(1, "08:00", "08:50"),
        TimeSlotJsonModel(2, "08:55", "09:45"),
        TimeSlotJsonModel(3, "10:15", "11:05"),
        TimeSlotJsonModel(4, "11:10", "12:00"),
        TimeSlotJsonModel(5, "14:00", "14:50"),
        TimeSlotJsonModel(6, "14:55", "15:45"),
        TimeSlotJsonModel(7, "16:15", "17:05"),
        TimeSlotJsonModel(8, "17:10", "18:00"),
        TimeSlotJsonModel(9, "19:00", "19:50"),
        TimeSlotJsonModel(10, "19:55", "20:45"),
        TimeSlotJsonModel(11, "20:50", "21:40"),
        TimeSlotJsonModel(12, "21:45", "22:35")
    )

    private val json = Json { isLenient = true }

    fun decodeEvalResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            runCatching { json.decodeFromString<String>(trimmed) }.getOrNull()
        } else {
            trimmed
        }
    }

    fun extractCourseDomHtmlJs(isYjs: Boolean): String {
        return if (isYjs) {
            """
            (function() {
                try {
                    var frame = document.getElementById('PageFrame') || (window.frames && window.frames['PageFrame']);
                    var doc = frame && (frame.contentDocument || (frame.contentWindow && frame.contentWindow.document) || frame.document);
                    if (doc && doc.documentElement) {
                        return doc.documentElement.outerHTML;
                    }
                } catch (e) {}
                return document.documentElement ? document.documentElement.outerHTML : '';
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var header = document.querySelector('h6.pull-left');
                    var table = document.getElementById('kbgrid_table_0');
                    if (table) {
                        return (header ? header.outerHTML : '') + table.outerHTML;
                    }
                } catch (e) {}
                return document.documentElement ? document.documentElement.outerHTML : '';
            })();
            """.trimIndent()
        }
    }

    fun extractJwxtGridHtmlJs(): String {
        return """
        (function() {
            try {
                var grid = document.getElementById('tabGrid');
                if (grid) return grid.outerHTML;
            } catch (e) {}
            return document.documentElement ? document.documentElement.outerHTML : '';
        })();
        """.trimIndent()
    }

    fun autoFillAndNavigateJs(url: String, username: String, password: String): String? {
        val uLit = JsonPrimitive(username).toString()
        val pLit = JsonPrimitive(password).toString()
        return when {
            url.contains("jwxt.cumt.edu.cn") && url.contains("login") && username.isNotBlank() -> """
                (function() {
                    var yhm = document.getElementById('yhm');
                    if (yhm && !yhm.value) yhm.value = $uLit;
                    var mm = document.getElementById('mm');
                    if (mm && !mm.value) mm.value = $pLit;
                })();
            """.trimIndent()
            url.contains("authserver.cumt.edu.cn/authserver/login") && username.isNotBlank() -> """
                (function() {
                    var u = document.getElementById('username');
                    var p = document.getElementById('password');
                    if (u && !u.value) u.value = $uLit;
                    if (p && !p.value) p.value = $pLit;
                })();
            """.trimIndent()
            url.contains("yjsxt.cumt.edu.cn/Gstudent/Default.aspx") -> """
                setTimeout(function() {
                    try {
                        var mf = document.getElementById('MenuFrame');
                        var doc = mf && (mf.contentDocument || (mf.contentWindow && mf.contentWindow.document));
                        var link = doc && doc.querySelector('#tree1_4_a');
                        if (link) link.click();
                    } catch (e) {}
                }, 600);
            """.trimIndent()
            else -> null
        }
    }
}

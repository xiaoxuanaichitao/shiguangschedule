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

    fun mobileAdaptationJs(url: String): String? {
        if (!url.contains("cumt.edu.cn", ignoreCase = true)) return null
        return """
        (function() {
            try {
                var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                if (head) {
                    var vp = document.querySelector('meta[name="viewport"]');
                    if (!vp) {
                        vp = document.createElement('meta');
                        vp.setAttribute('name', 'viewport');
                        head.appendChild(vp);
                    }
                    vp.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');

                    var href = (window.location && window.location.href) ? window.location.href : '';
                    var isJwxt = href.indexOf('jwxt.cumt.edu.cn') !== -1;
                    var isLoginPage = isJwxt && (document.getElementById('yhm') !== null || href.indexOf('login') !== -1);
                    var isAuthOrYjs = href.indexOf('authserver.cumt.edu.cn') !== -1 || href.indexOf('yjsxt.cumt.edu.cn') !== -1;

                    var css = '';
                    if (isLoginPage) {
                        css = [
                            'html, body { width: 100% !important; min-width: 0 !important; overflow-x: hidden !important; background: #f4f7fa !important; }',
                            '.col-sm-8, .sl_log_lf, .login_left, #login_left, .carousel, footer, .footer, .copyright, .login_bg_left { display: none !important; }',
                            '.container, .row { width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 auto !important; padding: 0 12px !important; box-sizing: border-box !important; }',
                            '.col-sm-4, .sl_log_rt, #login_right { width: 100% !important; max-width: 440px !important; float: none !important; margin: 18px auto !important; padding: 20px 18px !important; background: #ffffff !important; border-radius: 16px !important; box-shadow: 0 4px 20px rgba(0,0,0,0.06) !important; box-sizing: border-box !important; }',
                            '#yhm, #mm, #yzm, input[name="yhm"], input[name="mm"], input[name="yzm"] { height: 46px !important; min-height: 46px !important; font-size: 16px !important; border-radius: 10px !important; box-sizing: border-box !important; }',
                            '#yzmPic, img[src*="kaptcha"], img[src*="yzm"] { height: 46px !important; min-width: 112px !important; width: auto !important; border-radius: 8px !important; object-fit: contain !important; cursor: pointer !important; }',
                            '#dl, .btn-primary { width: 100% !important; height: 46px !important; font-size: 16px !important; border-radius: 10px !important; }'
                        ].join('\n');
                    } else if (isJwxt) {
                        css = [
                            'html, body { max-width: 100vw !important; overflow-x: auto !important; }',
                            'select, button, .btn, input[type="text"], input[type="button"], input[type="submit"] { min-height: 36px !important; font-size: 14px !important; }',
                            '.ui-jqgrid, .ui-jqgrid-view, .ui-jqgrid-bdiv, #kbgrid_table_0, #tabGrid { max-width: 100% !important; overflow-x: auto !important; -webkit-overflow-scrolling: touch !important; }'
                        ].join('\n');
                    } else if (isAuthOrYjs) {
                        css = [
                            'html, body { width: 100% !important; min-width: 0 !important; overflow-x: hidden !important; }',
                            'input[type="text"], input[type="password"], input:not([type]), select, button, .btn { font-size: 16px !important; min-height: 44px !important; box-sizing: border-box !important; }'
                        ].join('\n');
                    }

                    var styleEl = document.getElementById('cumt-mobile-adapt');
                    if (!styleEl) {
                        styleEl = document.createElement('style');
                        styleEl.id = 'cumt-mobile-adapt';
                        head.appendChild(styleEl);
                    }
                    styleEl.textContent = css;
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }

    fun autoFillAndNavigateJs(url: String, username: String, password: String): String? {
        val uLit = JsonPrimitive(username).toString()
        val pLit = JsonPrimitive(password).toString()
        return when {
            url.contains("jwxt.cumt.edu.cn") && url.contains("login") && username.isNotBlank() -> """
                (function() {
                    function applyJwxtFill() {
                        try {
                            var yhm = document.getElementById('yhm') || document.querySelector('input[name="yhm"]');
                            if (yhm && !yhm.value) {
                                yhm.value = $uLit;
                                yhm.dispatchEvent(new Event('input', { bubbles: true }));
                                yhm.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                            var mm = document.getElementById('mm') || document.querySelector('input[name="mm"]');
                            if (mm && !mm.value) {
                                mm.value = $pLit;
                                mm.dispatchEvent(new Event('input', { bubbles: true }));
                                mm.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                            var yzm = document.getElementById('yzm') || document.querySelector('input[name="yzm"]');
                            if (yzm && !yzm.value) {
                                yzm.focus();
                                if (typeof yzm.scrollIntoView === 'function') {
                                    yzm.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                }
                            }
                        } catch (e) {}
                    }
                    applyJwxtFill();
                    setTimeout(applyJwxtFill, 350);
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

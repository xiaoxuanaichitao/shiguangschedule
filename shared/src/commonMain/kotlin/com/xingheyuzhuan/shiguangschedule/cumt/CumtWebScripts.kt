package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.TimeSlotJsonModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class JwxtSelectOption(
    val value: String,
    val label: String,
    val selected: Boolean = false
)

@Serializable
data class JwxtTermOptionsState(
    val hasSelectors: Boolean = false,
    val years: List<JwxtSelectOption> = emptyList(),
    val terms: List<JwxtSelectOption> = emptyList(),
    val selectedYear: String = "",
    val selectedTerm: String = "",
    val rowCount: Int = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val isLoading: Boolean = false,
    val hasNextPage: Boolean = false
)

@Serializable
data class JwxtBkFetchState(
    val done: Boolean = false,
    val json: String = "",
    val xnm: String = "",
    val xqm: String = "",
    val error: String = ""
)

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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decodeEvalResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            runCatching { json.decodeFromString<String>(trimmed) }.getOrNull()
        } else {
            trimmed
        }
    }

    fun parseJwxtTermOptionsState(rawEvalResult: String?): JwxtTermOptionsState {
        val decoded = decodeEvalResult(rawEvalResult)?.trim().orEmpty()
        if (decoded.isEmpty() || !decoded.startsWith("{")) return JwxtTermOptionsState()
        return runCatching {
            json.decodeFromString<JwxtTermOptionsState>(decoded)
        }.getOrDefault(JwxtTermOptionsState())
    }

    fun parseJwxtBkFetchState(rawEvalResult: String?): JwxtBkFetchState {
        val decoded = decodeEvalResult(rawEvalResult)?.trim().orEmpty()
        if (decoded.isEmpty() || !decoded.startsWith("{")) return JwxtBkFetchState(done = true)
        return runCatching {
            json.decodeFromString<JwxtBkFetchState>(decoded)
        }.getOrDefault(JwxtBkFetchState(done = true))
    }

    fun jwxtCourseAjaxHookJs(): String {
        return """
        (function() {
            try {
                if (window.__cumtKbHookInstalled) return;
                var host = (window.location && window.location.host) ? window.location.host : '';
                if (host.indexOf('jwxt.cumt.edu.cn') === -1) return;
                window.__cumtKbHookInstalled = true;

                var origOpen = XMLHttpRequest.prototype.open;
                var origSend = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        this._cumtUrl = url ? String(url) : '';
                    } catch (e) {}
                    return origOpen.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function(body) {
                    try {
                        var xhr = this;
                        var url = xhr._cumtUrl || '';
                        if (url.indexOf('xskbcx_cxXsgrkb') !== -1) {
                            xhr._cumtBody = (typeof body === 'string') ? body : '';
                            xhr.addEventListener('load', function() {
                                try {
                                    if (xhr.status >= 200 && xhr.status < 300 && typeof xhr.responseText === 'string' && xhr.responseText.indexOf('kbList') !== -1) {
                                        var xnmEl = document.getElementById('xnm') || document.querySelector('select[name="xnm"]');
                                        var xqmEl = document.getElementById('xqm') || document.querySelector('select[name="xqm"]');
                                        window.__cumtLastKbJson = xhr.responseText;
                                        window.__cumtLastKbUrl = url;
                                        window.__cumtLastKbBody = xhr._cumtBody || '';
                                        window.__cumtLastKbXnm = xnmEl ? (xnmEl.value || '') : '';
                                        window.__cumtLastKbXqm = xqmEl ? (xqmEl.value || '') : '';
                                    }
                                } catch (e) {}
                            });
                        }
                    } catch (e) {}
                    return origSend.apply(this, arguments);
                };
            } catch (e) {}
        })();
        """.trimIndent()
    }

    fun fetchBkCourseJsonStartJs(): String {
        return """
        (function() {
            try {
                var xnmEl = document.getElementById('xnm') || document.querySelector('select[name="xnm"]');
                var xqmEl = document.getElementById('xqm') || document.querySelector('select[name="xqm"]');
                var xnm = xnmEl ? (xnmEl.value || '') : '';
                var xqm = xqmEl ? (xqmEl.value || '') : '';
                var cached = window.__cumtLastKbJson || '';
                if (cached && cached.indexOf('"kbList"') !== -1 &&
                    (window.__cumtLastKbXnm || '') === xnm &&
                    (window.__cumtLastKbXqm || '') === xqm) {
                    window.__cumtKbFetchState = { done: true, json: cached, xnm: xnm, xqm: xqm, error: '' };
                    return 'cached';
                }

                var m = window.location && window.location.search ? window.location.search.match(/[?&]gnmkdm=([^&#]+)/) : null;
                var gnmkdm = m ? decodeURIComponent(m[1]) : 'N2151';
                var suEl = document.getElementById('sessionUserKey') || document.querySelector('input[name="sessionUserKey"]');
                var su = suEl ? (suEl.value || '') : '';
                var url = window.__cumtLastKbUrl || ('/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + encodeURIComponent(gnmkdm) + (su ? '&su=' + encodeURIComponent(su) : ''));
                var body = (window.__cumtLastKbBody && (window.__cumtLastKbXnm || '') === xnm && (window.__cumtLastKbXqm || '') === xqm)
                    ? window.__cumtLastKbBody
                    : ('xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) + '&kzlx=ck&xsdm=');

                window.__cumtKbFetchState = { done: false, json: '', xnm: xnm, xqm: xqm, error: '' };
                fetch(url, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: body
                }).then(function(resp) {
                    return resp.text();
                }).then(function(text) {
                    window.__cumtKbFetchState = { done: true, json: text || '', xnm: xnm, xqm: xqm, error: '' };
                }).catch(function(err) {
                    window.__cumtKbFetchState = { done: true, json: '', xnm: xnm, xqm: xqm, error: String(err) };
                });
                return 'fetching';
            } catch (e) {
                window.__cumtKbFetchState = { done: true, json: '', xnm: '', xqm: '', error: String(e) };
                return 'error';
            }
        })();
        """.trimIndent()
    }

    fun readBkCourseJsonResultJs(): String {
        return """
        (function() {
            try {
                if (window.__cumtKbFetchState) {
                    return JSON.stringify(window.__cumtKbFetchState);
                }
                return JSON.stringify({
                    done: true,
                    json: window.__cumtLastKbJson || '',
                    xnm: window.__cumtLastKbXnm || '',
                    xqm: window.__cumtLastKbXqm || '',
                    error: ''
                });
            } catch (e) {
                return JSON.stringify({ done: true, json: '', xnm: '', xqm: '', error: String(e) });
            }
        })();
        """.trimIndent()
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
                try {
                    var mf = document.querySelector('iframe[name="mainFrame"], iframe#mainFrame');
                    var mDoc = mf && (mf.contentDocument || (mf.contentWindow && mf.contentWindow.document));
                    var tb = mDoc && mDoc.querySelector('iframe#tbxdm, iframe[name="tbxdm"]');
                    var tDoc = tb && (tb.contentDocument || (tb.contentWindow && tb.contentWindow.document));
                    if (tDoc && tDoc.documentElement) {
                        return tDoc.documentElement.outerHTML;
                    }
                } catch (e) {}
                return document.documentElement ? document.documentElement.outerHTML : '';
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var xnmEl = document.getElementById('xnm') || document.querySelector('select[name="xnm"]');
                    var xqmEl = document.getElementById('xqm') || document.querySelector('select[name="xqm"]');
                    var meta = '<div id="cumt-meta" data-cumt-xnm="' + (xnmEl ? (xnmEl.value || '') : '') + '" data-cumt-xqm="' + (xqmEl ? (xqmEl.value || '') : '') + '"></div>';
                    var header = document.querySelector('h6.pull-left');
                    var parts = [];
                    var grids = document.querySelectorAll('table[id^="kbgrid_table_"]');
                    for (var i = 0; i < grids.length; i++) {
                        parts.push(grids[i].outerHTML);
                    }
                    var listTable = document.getElementById('kblist_table');
                    if (listTable) {
                        parts.push(listTable.outerHTML);
                    }
                    if (parts.length > 0) {
                        return meta + (header ? header.outerHTML : '') + parts.join('');
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

    fun jwxtAutoNavigateJs(menuCode: String, targetPath: String): String {
        val codeLit = JsonPrimitive(menuCode).toString()
        val pathLit = JsonPrimitive(targetPath).toString()
        return """
        (function() {
            try {
                if (window.__cumtAutoNavDone) return;
                var host = (window.location && window.location.host) ? window.location.host : '';
                var pathname = (window.location && window.location.pathname) ? window.location.pathname : '';
                if (host.indexOf('jwxt.cumt.edu.cn') === -1) return;
                if (pathname.indexOf('/jwglxt/xtgl/index') === -1) return;
                window.__cumtAutoNavDone = true;
                var code = $codeLit;
                var target = $pathLit;
                setTimeout(function() {
                    try {
                        var link = document.querySelector('a[onclick*="' + code + '"]');
                        if (link && target.indexOf('/') !== 0) {
                            link.click();
                        } else {
                            window.location.replace(target);
                        }
                    } catch (e) {
                        window.location.replace(target);
                    }
                }, 250);
            } catch (e) {}
        })();
        """.trimIndent()
    }

    fun inspectJwxtPageStateJs(): String {
        return """
        (function() {
            function readSelect(id, name) {
                var el = document.getElementById(id) || document.querySelector('select[name="' + name + '"]');
                if (!el || !el.options) return { list: [], selected: '' };
                var list = [];
                var selected = el.value || '';
                for (var i = 0; i < el.options.length; i++) {
                    var opt = el.options[i];
                    var val = opt.value != null ? String(opt.value) : '';
                    var lbl = (opt.textContent || opt.innerText || opt.label || val).trim();
                    var isSel = !!opt.selected || (val === selected);
                    list.push({ value: val, label: lbl, selected: isSel });
                }
                return { list: list, selected: selected };
            }
            try {
                var xnm = readSelect('xnm', 'xnm');
                var xqm = readSelect('xqm', 'xqm');
                var rows = document.querySelectorAll('#tabGrid td[aria-describedby="tabGrid_kcmc"]');
                var loadEl = document.getElementById('load_tabGrid');
                var isLoading = false;
                if (loadEl) {
                    var st = window.getComputedStyle ? window.getComputedStyle(loadEl) : null;
                    isLoading = (loadEl.style.display !== 'none') && (!st || (st.display !== 'none' && st.visibility !== 'hidden'));
                }
                var pgInput = document.querySelector('input.ui-pg-input');
                var spTotal = document.getElementById('sp_1_pager');
                var nextEl = document.getElementById('next_pager');
                var curPage = pgInput ? (parseInt(pgInput.value, 10) || 1) : 1;
                var totalPages = spTotal ? (parseInt((spTotal.textContent || '').trim(), 10) || 0) : 0;
                var hasNext = !!(nextEl && !nextEl.classList.contains('ui-state-disabled'));
                if (totalPages > 0 && curPage >= totalPages) hasNext = false;
                return JSON.stringify({
                    hasSelectors: xnm.list.length > 0 || xqm.list.length > 0,
                    years: xnm.list,
                    terms: xqm.list,
                    selectedYear: xnm.selected,
                    selectedTerm: xqm.selected,
                    rowCount: rows ? rows.length : 0,
                    currentPage: curPage,
                    totalPages: totalPages,
                    isLoading: isLoading,
                    hasNextPage: hasNext
                });
            } catch (e) {
                return JSON.stringify({ hasSelectors: false });
            }
        })();
        """.trimIndent()
    }

    fun selectJwxtTermAndQueryJs(
        xnmValue: String?,
        xqmValue: String?,
        triggerSearch: Boolean = true
    ): String {
        val xnmLit = if (xnmValue != null) JsonPrimitive(xnmValue).toString() else "null"
        val xqmLit = if (xqmValue != null) JsonPrimitive(xqmValue).toString() else "null"
        val doSearchLit = if (triggerSearch) "true" else "false"
        return """
        (function() {
            function applySelect(id, name, val) {
                if (val === null) return false;
                var el = document.getElementById(id) || document.querySelector('select[name="' + name + '"]');
                if (!el) return false;
                el.value = val;
                el.dispatchEvent(new Event('change', { bubbles: true }));
                if (window.jQuery) {
                    try {
                        window.jQuery(el).val(val).trigger('change').trigger('chosen:updated');
                    } catch (e) {}
                }
                return true;
            }
            try {
                applySelect('xnm', 'xnm', $xnmLit);
                applySelect('xqm', 'xqm', $xqmLit);
                if ($doSearchLit) {
                    var btn = document.getElementById('search_go') || document.querySelector('button.btn_cx, #search_go');
                    if (btn) {
                        btn.click();
                        return 'clicked';
                    }
                }
                return 'updated';
            } catch (e) {
                return 'error';
            }
        })();
        """.trimIndent()
    }

    fun jwxtPagerActionJs(action: String): String {
        val selector = if (action == "first") "#first_pager" else "#next_pager"
        val selLit = JsonPrimitive(selector).toString()
        return """
        (function() {
            try {
                var el = document.querySelector($selLit);
                if (!el || el.classList.contains('ui-state-disabled')) return 'disabled';
                var span = el.querySelector('.ui-icon');
                el.click();
                if (span && el.classList.contains('ui-pg-button') === false) span.click();
                return 'clicked';
            } catch (e) {
                return 'disabled';
            }
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
                            '.logo_1, .sl_top, .col-sm-8, .sl_log_lf, .login_left, #login_left, .carousel, footer, .footer, .copyright, .login_bg_left { display: none !important; }',
                            '.container, .row { width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 auto !important; padding: 0 12px !important; box-sizing: border-box !important; }',
                            '.col-sm-4, .sl_log_rt, #login_right { width: 100% !important; max-width: 440px !important; float: none !important; margin: 18px auto !important; padding: 20px 16px !important; background: #ffffff !important; border-radius: 16px !important; box-shadow: 0 4px 20px rgba(0,0,0,0.06) !important; box-sizing: border-box !important; }',
                            '.sl_log_rt form, #login_right form, .sl_log_rt .form-group, #login_right .form-group, .sl_log_rt [class*="col-"], #login_right [class*="col-"] { width: 100% !important; max-width: 100% !important; margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important; padding-right: 0 !important; float: none !important; box-sizing: border-box !important; }',
                            '#yzmDiv, .yzm { display: flex !important; flex-direction: row !important; flex-wrap: wrap !important; align-items: center !important; gap: 10px !important; width: 100% !important; }',
                            '#yzmDiv > *, .yzm > * { float: none !important; margin: 0 !important; box-sizing: border-box !important; }',
                            '#yzmDiv > input, .yzm > input, #yzm, input[name="yzm"] { flex: 1 1 130px !important; min-width: 120px !important; width: 100% !important; max-width: 100% !important; padding: 6px 12px !important; }',
                            '#yhm, #mm, #yzm, input[name="yhm"], input[name="mm"], input[name="yzm"] { height: 46px !important; min-height: 46px !important; font-size: 16px !important; border-radius: 10px !important; box-sizing: border-box !important; }',
                            '#yzmPic, img[src*="kaptcha"], img[src*="yzm"] { flex: 0 0 auto !important; height: 44px !important; width: auto !important; max-width: 140px !important; min-width: 0 !important; border-radius: 8px !important; object-fit: contain !important; cursor: pointer !important; display: block !important; }',
                            '#dl, .btn-primary { width: 100% !important; height: 46px !important; font-size: 16px !important; border-radius: 10px !important; }'
                        ].join('\n');
                    } else if (isJwxt) {
                        css = [
                            'html, body { max-width: 100vw !important; overflow-x: auto !important; }',
                            '.chosen-container, .chosen-container-single, .chosen-container-multi { display: none !important; }',
                            'select, select.form-control, select.chosen-select, #xnm, #xqm, #kcbj, #kc { display: block !important; visibility: visible !important; opacity: 1 !important; position: static !important; width: 100% !important; max-width: 100% !important; min-height: 40px !important; height: auto !important; padding: 6px 10px !important; margin-top: 4px !important; border: 1px solid #cbd5e1 !important; border-radius: 8px !important; background-color: #ffffff !important; color: #1e293b !important; font-size: 15px !important; -webkit-appearance: menulist !important; appearance: menulist !important; pointer-events: auto !important; }',
                            '#searchForm .form-group, #searchForm [class*="col-"], .form-horizontal .form-group { overflow: visible !important; height: auto !important; }',
                            'button, .btn, input[type="text"], input[type="button"], input[type="submit"] { min-height: 36px !important; font-size: 14px !important; }',
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

                    if (isLoginPage) {
                        function fixLoginCaptchaRow() {
                            try {
                                function findRowChild(el) {
                                    var cur = el;
                                    for (var d = 0; d < 4 && cur && cur.parentElement; d++) {
                                        var p = cur.parentElement;
                                        if (p.id === 'yzmDiv' || p.classList.contains('yzm')) return cur;
                                        cur = p;
                                    }
                                    return null;
                                }
                                var yzmInput = document.getElementById('yzm') || document.querySelector('input[name="yzm"]');
                                if (yzmInput) {
                                    yzmInput.style.setProperty('min-width', '120px', 'important');
                                    yzmInput.style.setProperty('padding', '6px 12px', 'important');
                                    yzmInput.style.setProperty('box-sizing', 'border-box', 'important');
                                    var inputChild = findRowChild(yzmInput);
                                    if (inputChild && inputChild !== yzmInput) {
                                        inputChild.style.setProperty('flex', '1 1 130px', 'important');
                                        inputChild.style.setProperty('min-width', '120px', 'important');
                                        inputChild.style.setProperty('width', 'auto', 'important');
                                        yzmInput.style.setProperty('width', '100%', 'important');
                                    } else {
                                        yzmInput.style.setProperty('flex', '1 1 130px', 'important');
                                        yzmInput.style.setProperty('width', 'auto', 'important');
                                    }
                                }
                                var yzmImg = document.getElementById('yzmPic') || document.querySelector('img[src*="kaptcha"], img[src*="yzm"]');
                                if (yzmImg) {
                                    yzmImg.style.setProperty('max-width', '140px', 'important');
                                    yzmImg.style.setProperty('height', '44px', 'important');
                                    yzmImg.style.setProperty('width', 'auto', 'important');
                                    var imgChild = findRowChild(yzmImg);
                                    if (imgChild && imgChild !== yzmImg) {
                                        imgChild.style.setProperty('flex', '0 0 auto', 'important');
                                        imgChild.style.setProperty('width', 'auto', 'important');
                                    }
                                }
                            } catch (e) {}
                        }
                        fixLoginCaptchaRow();
                        setTimeout(fixLoginCaptchaRow, 300);
                    } else if (isJwxt) {
                        function forceNativeSelectsAndHideQr() {
                            try {
                                var selects = document.querySelectorAll('select');
                                for (var i = 0; i < selects.length; i++) {
                                    var s = selects[i];
                                    s.style.setProperty('display', 'block', 'important');
                                    s.style.setProperty('visibility', 'visible', 'important');
                                    s.style.setProperty('opacity', '1', 'important');
                                }
                                var chosens = document.querySelectorAll('.chosen-container');
                                for (var j = 0; j < chosens.length; j++) {
                                    chosens[j].style.setProperty('display', 'none', 'important');
                                }
                                var allEls = document.querySelectorAll('div, aside');
                                for (var k = 0; k < allEls.length; k++) {
                                    var el = allEls[k];
                                    var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
                                    if (st && st.position === 'fixed' && (el.innerText || '').indexOf('反馈') !== -1 && el.querySelector('img')) {
                                        el.style.setProperty('display', 'none', 'important');
                                    }
                                }
                            } catch (e) {}
                        }
                        forceNativeSelectsAndHideQr();
                        setTimeout(forceNativeSelectsAndHideQr, 500);
                    }
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

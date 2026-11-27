/**
 * 矿大时光课表 · 免费 AI 体验中转通道 (Cloudflare Workers)
 *
 * 特点：
 * 1. 零服务器成本：直接运行在 Cloudflare Workers 免费额度（10 万次请求/天）。
 * 2. Key 安全隔离：上游免费 API Key（如智谱 glm-4-flash / glm-4v-flash 或魔搭 ModelScope）
 *    仅存放在 Cloudflare Secrets 中，绝不打包进 APK 或源码仓库。
 * 3. 自动路由文字与视觉模型：
 *    - 纯文本对话 / 课表工具调用 -> TEXT_MODEL（默认 glm-4-flash-250414，永久免费）
 *    - 含实验表/课表图片 -> VISION_MODEL（默认 glm-4.6v-flash，永久免费）
 * 4. 按设备每日限流：基于 X-Shiguang-Device-Id（兜底 CF-Connecting-IP）按北京时间每日限额，
 *    可选绑定 KV 命名空间 QUOTA_KV 实现跨节点持久计数，未绑定时自动降级为内存计数。
 * 5. 支持逗号分隔多把 UPSTREAM_API_KEY 自动轮询分摊并发。
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Shiguang-Client, X-Shiguang-Device-Id",
  "Access-Control-Expose-Headers": "X-Free-Remaining, X-Free-Limit, X-Routed-Model",
};

const MAX_BODY_BYTES = 4 * 1024 * 1024; // 4 MB，足够容纳压缩后的 1920px 实验表 JPEG
const memoryQuota = new Map();

function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

function getBeijingDateKey() {
  const now = new Date(Date.now() + 8 * 60 * 60 * 1000);
  return now.toISOString().slice(0, 10);
}

function getClientIdentifier(request) {
  const rawDeviceId = (request.headers.get("X-Shiguang-Device-Id") || "").trim();
  const safeDeviceId = rawDeviceId.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
  if (safeDeviceId.length >= 8) {
    return `dev:${safeDeviceId}`;
  }
  const ip = (request.headers.get("CF-Connecting-IP") || "unknown").trim();
  return `ip:${ip}`;
}

function pickUpstreamKey(rawKeys) {
  const list = String(rawKeys || "")
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (list.length === 0) return "";
  return list[Math.floor(Math.random() * list.length)];
}

function hasImageInMessages(messages) {
  if (!Array.isArray(messages)) return false;
  return messages.some((msg) => {
    if (!msg || !Array.isArray(msg.content)) return false;
    return msg.content.some(
      (part) =>
        part &&
        (part.type === "image_url" ||
          part.type === "input_image" ||
          Boolean(part.image_url))
    );
  });
}

function resolveModels(env) {
  const textModel = (env.TEXT_MODEL || "glm-4-flash-250414").trim();
  const visionModel = (env.VISION_MODEL || "glm-4.6v-flash").trim();
  return { textModel, visionModel };
}

function resolveUpstreamChatUrl(env) {
  const base = (env.UPSTREAM_BASE_URL || "https://open.bigmodel.cn/api/paas/v4")
    .trim()
    .replace(/\/+$/, "");
  if (base.toLowerCase().endsWith("/chat/completions")) return base;
  return `${base}/chat/completions`;
}

async function consumeDailyQuota(env, clientId, dailyLimit) {
  const dateKey = getBeijingDateKey();
  const storageKey = `q:${dateKey}:${clientId}`;

  if (env.QUOTA_KV && typeof env.QUOTA_KV.get === "function") {
    const raw = await env.QUOTA_KV.get(storageKey);
    const used = Number.parseInt(raw || "0", 10) || 0;
    if (used >= dailyLimit) {
      return { allowed: false, used, remaining: 0 };
    }
    const nextUsed = used + 1;
    await env.QUOTA_KV.put(storageKey, String(nextUsed), { expirationTtl: 90_000 });
    return { allowed: true, used: nextUsed, remaining: Math.max(0, dailyLimit - nextUsed) };
  }

  // 未配置 KV 时自动降级为单实例内存计数（零配置也能直接用）
  if (memoryQuota.size > 4000) {
    memoryQuota.clear();
  }
  const used = memoryQuota.get(storageKey) || 0;
  if (used >= dailyLimit) {
    return { allowed: false, used, remaining: 0 };
  }
  const nextUsed = used + 1;
  memoryQuota.set(storageKey, nextUsed);
  return { allowed: true, used: nextUsed, remaining: Math.max(0, dailyLimit - nextUsed) };
}

async function handleModels(env) {
  const { textModel, visionModel } = resolveModels(env);
  const nowSec = Math.floor(Date.now() / 1000);
  const ids = ["cumt-free-auto", textModel, visionModel].filter(
    (v, idx, arr) => v && arr.indexOf(v) === idx
  );
  return jsonResponse({
    object: "list",
    data: ids.map((id) => ({
      id,
      object: "model",
      created: nowSec,
      owned_by: "shiguang-free-proxy",
    })),
  });
}

async function handleChatCompletions(request, env) {
  const requiredClientToken = (env.APP_CLIENT_TOKEN || "").trim();
  if (requiredClientToken) {
    const incomingToken = (request.headers.get("X-Shiguang-Client") || "").trim();
    if (incomingToken !== requiredClientToken) {
      return jsonResponse(
        { error: { message: "未授权的客户端请求，请更新至最新版矿大时光课表" } },
        403
      );
    }
  }

  const upstreamKey = pickUpstreamKey(env.UPSTREAM_API_KEY);
  if (!upstreamKey) {
    return jsonResponse(
      { error: { message: "免费中转尚未配置 UPSTREAM_API_KEY，请联系维护者在 Cloudflare Secrets 中设置" } },
      503
    );
  }

  const contentLength = Number.parseInt(request.headers.get("Content-Length") || "0", 10);
  if (contentLength > MAX_BODY_BYTES) {
    return jsonResponse(
      { error: { message: "请求内容过大，请压缩图片后重试（上限 4MB）" } },
      413
    );
  }

  const rawBody = await request.text();
  if (rawBody.length > MAX_BODY_BYTES) {
    return jsonResponse(
      { error: { message: "请求内容过大，请压缩图片后重试（上限 4MB）" } },
      413
    );
  }

  let body;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return jsonResponse({ error: { message: "请求体不是合法的 JSON" } }, 400);
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return jsonResponse({ error: { message: "messages 不能为空" } }, 400);
  }

  const dailyLimit = Math.max(1, Number.parseInt(env.DAILY_LIMIT || "20", 10) || 20);
  const clientId = getClientIdentifier(request);
  const quota = await consumeDailyQuota(env, clientId, dailyLimit);
  const quotaHeaders = {
    "X-Free-Remaining": String(quota.remaining),
    "X-Free-Limit": String(dailyLimit),
  };

  if (!quota.allowed) {
    return jsonResponse(
      {
        error: {
          message: `今日免费体验次数已用完（${dailyLimit}/${dailyLimit}），明天 0 点自动重置；也可以在「模型与 Key」中切换为你自己的 DeepSeek / OpenAI / Gemini API Key`,
        },
      },
      429,
      quotaHeaders
    );
  }

  const { textModel, visionModel } = resolveModels(env);
  const hasImage = hasImageInMessages(body.messages);
  const requestedModel = String(body.model || "").trim();

  // 严格限制仅允许路由到白名单免费模型，防止恶意刷付费模型
  let targetModel;
  if (hasImage) {
    targetModel = visionModel;
  } else if (requestedModel === textModel || requestedModel === visionModel) {
    targetModel = requestedModel;
  } else {
    targetModel = textModel;
  }

  const maxTokensCap = Math.max(256, Number.parseInt(env.MAX_TOKENS || "2048", 10) || 2048);
  const requestedMaxTokens = Number.parseInt(body.max_tokens, 10);
  const effectiveMaxTokens =
    Number.isFinite(requestedMaxTokens) && requestedMaxTokens > 0
      ? Math.min(requestedMaxTokens, maxTokensCap)
      : Math.min(1536, maxTokensCap);

  const upstreamPayload = {
    model: targetModel,
    messages: body.messages,
    temperature: typeof body.temperature === "number" ? body.temperature : 0.2,
    max_tokens: effectiveMaxTokens,
    stream: false,
  };

  // 纯文本模型透传 tools；视觉模型（如 glm-4v-flash）默认不带 tools 以避免上游报 400
  const allowVisionTools = String(env.VISION_SUPPORTS_TOOLS || "false").toLowerCase() === "true";
  if (Array.isArray(body.tools) && body.tools.length > 0 && (!hasImage || allowVisionTools)) {
    upstreamPayload.tools = body.tools;
    if (body.tool_choice) {
      upstreamPayload.tool_choice = body.tool_choice;
    }
  }

  const upstreamUrl = resolveUpstreamChatUrl(env);
  const sendToUpstream = async (payload) =>
    fetch(upstreamUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${upstreamKey}`,
      },
      body: JSON.stringify(payload),
    });

  let upstreamResp = await sendToUpstream(upstreamPayload);
  let respText = await upstreamResp.text();

  // 若上游某免费模型不支持 tools 参数导致 400，自动剥离 tools 重试一次
  if (
    upstreamResp.status === 400 &&
    upstreamPayload.tools &&
    /tool|function/i.test(respText)
  ) {
    delete upstreamPayload.tools;
    delete upstreamPayload.tool_choice;
    upstreamResp = await sendToUpstream(upstreamPayload);
    respText = await upstreamResp.text();
  }

  return new Response(respText, {
    status: upstreamResp.status,
    headers: {
      "Content-Type": upstreamResp.headers.get("Content-Type") || "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...quotaHeaders,
      "X-Routed-Model": targetModel,
    },
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    if (request.method === "GET" && (path === "/" || path === "/health")) {
      const { textModel, visionModel } = resolveModels(env);
      return jsonResponse({
        ok: true,
        service: "shiguang-free-ai-proxy",
        configured: Boolean((env.UPSTREAM_API_KEY || "").trim()),
        textModel,
        visionModel,
        dailyLimit: Number.parseInt(env.DAILY_LIMIT || "20", 10) || 20,
      });
    }

    if (request.method === "GET" && (path === "/v1/models" || path === "/models")) {
      return handleModels(env);
    }

    if (
      request.method === "POST" &&
      (path === "/v1/chat/completions" || path === "/chat/completions")
    ) {
      return handleChatCompletions(request, env);
    }

    return jsonResponse({ error: { message: `Not Found: ${path}` } }, 404);
  },
};

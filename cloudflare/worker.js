const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Content-Type": "application/json; charset=utf-8"
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders });
}

function bearer(request) {
  return (request.headers.get("Authorization") || "").replace(/^Bearer\\s+/i, "");
}

function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || !a || !b || a.length !== b.length) return false;
  let out = 0;
  for (let i = 0; i < a.length; i++) out |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return out === 0;
}

const ALLOWED = new Set([
  "OPEN_WHATSAPP", "OPEN_MUSICOLET", "PLAY", "PAUSE", "STOP", "NEXT", "PREVIOUS"
]);

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
    const url = new URL(request.url);

    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "RinBridge Relay", version: 1 });
    }

    if (url.pathname === "/v1/command" && request.method === "POST") {
      if (!safeEqual(bearer(request), env.ADMIN_TOKEN)) return json({ error: "unauthorized" }, 401);
      let body;
      try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
      const command = String(body.command || "").toUpperCase();
      const deviceId = String(body.device_id || "");
      if (!/^[a-z0-9-]{3,64}$/.test(deviceId) || !ALLOWED.has(command)) {
        return json({ error: "invalid_device_or_command" }, 400);
      }
      const id = crypto.randomUUID();
      const item = { id, device_id: deviceId, command, created_at: new Date().toISOString() };
      await env.COMMANDS.put("cmd:" + deviceId + ":" + id, JSON.stringify(item), { expirationTtl: 600 });
      return json({ ok: true, queued: true, id, command, device_id: deviceId }, 202);
    }

    if (url.pathname === "/v1/next" && request.method === "GET") {
      if (!safeEqual(bearer(request), env.DEVICE_TOKEN)) return json({ error: "unauthorized" }, 401);
      const deviceId = String(url.searchParams.get("device_id") || "");
      if (!/^[a-z0-9-]{3,64}$/.test(deviceId)) return json({ error: "invalid_device_id" }, 400);
      const listed = await env.COMMANDS.list({ prefix: "cmd:" + deviceId + ":", limit: 20 });
      const commands = [];
      for (const key of listed.keys) {
        const value = await env.COMMANDS.get(key.name, "json");
        if (value) commands.push(value);
      }
      commands.sort((a, b) => a.created_at.localeCompare(b.created_at));
      return json({ ok: true, commands });
    }

    if (url.pathname === "/v1/ack" && request.method === "POST") {
      if (!safeEqual(bearer(request), env.DEVICE_TOKEN)) return json({ error: "unauthorized" }, 401);
      let body;
      try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
      const deviceId = String(body.device_id || "");
      const id = String(body.id || "");
      if (!/^[a-z0-9-]{3,64}$/.test(deviceId) || !/^[0-9a-f-]{36}$/i.test(id)) {
        return json({ error: "invalid_ack" }, 400);
      }
      await env.COMMANDS.delete("cmd:" + deviceId + ":" + id);
      return json({ ok: true, acknowledged: id });
    }

    return json({ error: "not_found" }, 404);
  }
};

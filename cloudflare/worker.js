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
  return (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
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

    if (url.pathname === "/v1/transcribe" && request.method === "POST") {
      if (!safeEqual(bearer(request), env.DEVICE_TOKEN)) return json({ error: "unauthorized" }, 401);
      if (!env.GEMINI_API_KEY) return json({ error: "ai_not_configured" }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
      const audio = typeof body.audio === "string" ? body.audio : "";
      const mimeType = typeof body.mimeType === "string" ? body.mimeType : "audio/mp4";
      if (!audio || audio.length > 9000000 || !["audio/mp4", "audio/3gpp", "audio/aac", "audio/wav"].includes(mimeType)) return json({ error: "invalid_audio" }, 400);
      try {
        const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", {
          method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
          signal: AbortSignal.timeout(25000),
          body: JSON.stringify({ contents: [{ role: "user", parts: [
            { text: "Transkripsikan rakaman ini tepat dalam Bahasa Melayu Malaysia. Kekalkan nama dan nombor. Pulangkan teks transkripsi sahaja tanpa penerangan." },
            { inlineData: { mimeType, data: audio } }
          ] }], generationConfig: { temperature: 0.1, maxOutputTokens: 500 } })
        });
        const result = await upstream.json().catch(() => ({}));
        if (!upstream.ok) return json({ error: "transcription_failed", message: result?.error?.message || "Pengecaman audio gagal." }, 502);
        const transcript = result?.candidates?.[0]?.content?.parts?.map(p => typeof p.text === "string" ? p.text : "").join("").trim();
        if (!transcript) return json({ error: "empty_transcript" }, 502);
        return json({ ok: true, transcript });
      } catch (_error) { return json({ error: "transcription_timeout" }, 504); }
    }

    if (url.pathname === "/v1/tts" && request.method === "POST") {
      if (!safeEqual(bearer(request), env.DEVICE_TOKEN)) return json({ error: "unauthorized" }, 401);
      if (!env.GEMINI_API_KEY) return json({ error: "ai_not_configured" }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
      const text = typeof body.text === "string" ? body.text.trim() : "";
      if (!text || text.length > 3000) return json({ error: "invalid_text" }, 400);
      try {
        const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-tts:generateContent", {
          method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
          signal: AbortSignal.timeout(35000),
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: "Read the following exactly in a gentle, warm, calm female voice. Speak naturally in Standard Malay with a Malaysian accent, slightly affectionate and emotionally kind. Use a relaxed pace and clear pronunciation. Do not read these instructions aloud.\n\n" + text }] }],
            generationConfig: { responseModalities: ["AUDIO"], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "Vindemiatrix" } } } }
          })
        });
        const result = await upstream.json().catch(() => ({}));
        if (!upstream.ok) return json({ error: "tts_failed", message: result?.error?.message || "Penjanaan suara gagal." }, 502);
        const audioPart = result?.candidates?.[0]?.content?.parts?.find(p => p.inlineData?.data);
        if (!audioPart) return json({ error: "empty_audio" }, 502);
        return json({ ok: true, audio: audioPart.inlineData.data, mimeType: audioPart.inlineData.mimeType || "audio/wav" });
      } catch (_error) { return json({ error: "tts_timeout" }, 504); }
    }

    if (url.pathname === "/v1/chat" && request.method === "POST") {
      if (!safeEqual(bearer(request), env.DEVICE_TOKEN)) return json({ error: "unauthorized" }, 401);
      if (!env.GEMINI_API_KEY) return json({ error: "ai_not_configured" }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
      const inputMessages = Array.isArray(body.messages) ? body.messages : [];
      if (inputMessages.length < 1 || inputMessages.length > 20) {
        return json({ error: "invalid_messages" }, 400);
      }
      const contents = [];
      for (const item of inputMessages) {
        const role = item?.role === "model" ? "model" : item?.role === "user" ? "user" : null;
        const message = typeof item?.text === "string" ? item.text.trim() : "";
        if (!role || !message || message.length > 6000) return json({ error: "invalid_message" }, 400);
        contents.push({ role, parts: [{ text: message }] });
      }
      const systemInstruction = {
        parts: [{ text: "Nama anda Rin, pembantu AI peribadi pengguna. Balas dalam Bahasa Melayu Malaysia secara lembut, tenang, mesra dan jelas. Panggil pengguna sayang apabila sesuai. Jangan mendakwa telah melakukan tindakan yang belum dilakukan. Jika tidak pasti, nyatakan dengan jujur." }]
      };
      const models = ["gemini-3.5-flash-lite", "gemini-3.8-flash"];
      let lastStatus = 502;
      let lastMessage = "Pembekal AI tidak dapat memproses permintaan.";
      for (const model of models) {
        try {
          const upstream = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "x-goog-api-key": env.GEMINI_API_KEY
            },
            signal: AbortSignal.timeout(25000),
            body: JSON.stringify({
              systemInstruction,
              contents,
              generationConfig: { temperature: 0.7, maxOutputTokens: 700 }
            })
          });
          const result = await upstream.json().catch(() => ({}));
          if (!upstream.ok) {
            lastStatus = upstream.status;
            lastMessage = result?.error?.message || "Pembekal AI tidak dapat memproses permintaan.";
            // Only switch model for temporary capacity/rate-limit failures.
            if (upstream.status === 429 || upstream.status === 503 || upstream.status === 502) continue;
            return json({ error: "ai_upstream_error", status: upstream.status, message: lastMessage }, 502);
          }
          const reply = result?.candidates?.[0]?.content?.parts
            ?.map(part => typeof part.text === "string" ? part.text : "")
            .join("").trim();
          if (!reply) return json({ error: "empty_ai_reply", model }, 502);
          return json({ ok: true, reply, model });
        } catch (_error) {
          lastStatus = 502;
          lastMessage = "Rangkaian ke pembekal AI terganggu.";
        }
      }
      return json({ error: "ai_upstream_error", status: lastStatus, message: lastMessage,
        tried_models: models }, 503);
    }

    if (!env.COMMANDS) return json({ error: "kv_binding_missing" }, 500);

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

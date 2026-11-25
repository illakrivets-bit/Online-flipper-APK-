export interface Env {
  SESSIONS: DurableObjectNamespace;
}

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });

function randomToken(bytes = 24): string {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return Array.from(data, b => b.toString(16).padStart(2, "0")).join("");
}

async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") return json({ ok: true, service: "online-flipper-pairing" });
    if (url.pathname === "/api/sessions" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { duration?: string } | null;
      if (!body || body.duration !== "24h") {
        return json({ error: "Only 24-hour access sessions are supported" }, 400);
      }
      const id = randomToken(12);
      const ownerToken = randomToken(32);
      const joinCode = randomToken(16);
      const stub = env.SESSIONS.get(env.SESSIONS.idFromName(id));
      const response = await stub.fetch("https://session.internal/create", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          ownerHash: await digest(ownerToken),
          joinCodeHash: await digest(joinCode),
          duration: "24h",
        }),
      });
      if (!response.ok) return json({ error: "could not create session" }, 500);
      return json({
        sessionId: id,
        ownerToken,
        qrPayload: JSON.stringify({ v: 1, service: "online-flipper-zero", sessionId: id, joinCode }),
        duration: body.duration,
        expiresInSeconds: 86400,
      }, 201);
    }
    const match = url.pathname.match(/^\/api\/sessions\/([a-f0-9]+)(\/.*)?$/);
    if (!match) return json({ error: "not found" }, 404);
    const stub = env.SESSIONS.get(env.SESSIONS.idFromName(match[1]));
    return stub.fetch(new Request("https://session.internal" + (match[2] || "/"), request));
  },
};

export class PairingSession {
  private sockets = new Set<WebSocket>();

  constructor(private state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/create" && request.method === "POST") {
      const input = await request.json() as {
        ownerHash: string; joinCodeHash: string; duration: "permanent" | "24h";
      };
      const now = Date.now();
      await this.state.storage.put("session", {
        ownerHash: input.ownerHash,
        joinCodeHash: input.joinCodeHash,
        duration: input.duration,
        createdAt: now,
        expiresAt: input.duration === "24h" ? now + 86400000 : null,
        pending: null,
        clientHash: null,
        revoked: false,
      });
      if (input.duration === "24h") await this.state.storage.setAlarm(now + 86400000);
      return json({ ok: true });
    }

    const session = await this.state.storage.get<any>("session");
    if (!session || session.revoked) return json({ error: "session unavailable" }, 404);
    if (session.expiresAt && Date.now() >= session.expiresAt) {
      await this.state.storage.put("session", { ...session, revoked: true });
      return json({ error: "session expired" }, 410);
    }

    if (url.pathname === "/join" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { joinCode?: string; deviceName?: string } | null;
      if (!body?.joinCode || await digest(body.joinCode) !== session.joinCodeHash) {
        return json({ error: "invalid QR invitation" }, 401);
      }
      if (session.clientHash || session.pending) return json({ error: "invitation already used or a device is already paired" }, 409);
      const requestToken = randomToken(32);
      const requestId = randomToken(8);
      await this.state.storage.put("session", {
        ...session,
        joinCodeHash: null,
        pending: { requestId, requestHash: await digest(requestToken), deviceName: (body.deviceName || "Android device").slice(0, 48) },
      });
      return json({ requestId, requestToken, status: "waiting_for_owner" }, 202);
    }

    if (url.pathname === "/join/status" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { requestToken?: string } | null;
      if (!body?.requestToken) return json({ error: "invalid join request" }, 401);
      const requestHash = await digest(body.requestToken);
      if (session.pending && requestHash === session.pending.requestHash) {
        return json({ status: "waiting_for_owner" }, 200);
      }
      if (session.approvedRequestHash && requestHash === session.approvedRequestHash && session.clientToken) {
        return json({ status: "approved", accessToken: session.clientToken }, 200);
      }
      return json({ error: "invalid or expired join request" }, 401);
    }

    if (url.pathname === "/approve" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { ownerToken?: string; requestId?: string } | null;
      if (!body?.ownerToken || await digest(body.ownerToken) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      if (!session.pending || body.requestId !== session.pending.requestId) return json({ error: "pending request not found" }, 404);
      const accessToken = randomToken(32);
      await this.state.storage.put("session", { ...session, clientToken: accessToken, clientHash: await digest(accessToken), approvedRequestHash: session.pending.requestHash, pending: null });
      return json({ status: "paired", deviceName: session.pending.deviceName });
    }

    if (url.pathname === "/status" && request.method === "GET") {
      const supplied = request.headers.get("x-owner-token") || "";
      if (!supplied || await digest(supplied) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      return json({
        status: session.clientHash ? "paired" : session.pending ? "approval_pending" : "waiting",
        pendingRequest: session.pending ? { requestId: session.pending.requestId, deviceName: session.pending.deviceName } : null,
        duration: session.duration,
        expiresAt: session.expiresAt,
      });
    }

    if (url.pathname === "/revoke" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { ownerToken?: string } | null;
      if (!body?.ownerToken || await digest(body.ownerToken) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      await this.state.storage.put("session", { ...session, revoked: true, clientHash: null, clientToken: null, pending: null });
      for (const socket of this.sockets) socket.close(1000, "Session revoked");
      return json({ status: "revoked" });
    }

    if (url.pathname === "/ws" && request.headers.get("Upgrade") === "websocket") {
      const token = url.searchParams.get("token") || "";
      const tokenHash = await digest(token);
      const isOwner = tokenHash === session.ownerHash;
      const isPairedDevice = !!session.clientHash && tokenHash === session.clientHash;
      if (!isOwner && !isPairedDevice) return json({ error: "device authorization required" }, 401);
      const pair = new WebSocketPair();
      const client = pair[0];
      const server = pair[1];
      server.accept();
      this.sockets.add(server);
      server.addEventListener("close", () => this.sockets.delete(server));
      server.addEventListener("message", event => {
        if (typeof event.data !== "string" || event.data.length > 4096) return;
        try {
          const message = JSON.parse(event.data);
          if (!message || !["heartbeat", "device_status", "user_approved_event"].includes(message.type)) return;
          const safe = JSON.stringify({ type: message.type, payload: message.payload ?? null, at: Date.now() });
          for (const socket of this.sockets) if (socket !== server && socket.readyState === WebSocket.OPEN) socket.send(safe);
        } catch { /* Ignore malformed frames. */ }
      });
      return new Response(null, { status: 101, webSocket: client });
    }
    return json({ error: "not found" }, 404);
  }

  async alarm(): Promise<void> {
    const session = await this.state.storage.get<any>("session");
    if (!session || session.revoked) return;
    if (session.expiresAt && Date.now() >= session.expiresAt) {
      await this.state.storage.put("session", { ...session, revoked: true, clientHash: null, clientToken: null, pending: null });
      for (const socket of this.sockets) socket.close(1000, "Session expired");
    }
  }
}

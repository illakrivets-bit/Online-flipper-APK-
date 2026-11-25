export interface Env {
  SESSIONS: DurableObjectNamespace;
}

type PendingJoin = { requestId: string; requestHash: string; deviceName: string };
type Session = {
  ownerHash: string;
  joinCodeHash: string | null;
  createdAt: number;
  expiresAt: number | null;
  pending: PendingJoin | null;
  clientHash: string | null;
  clientToken?: string | null;
  approvedRequestHash?: string | null;
  revoked: boolean;
};

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
    if (url.pathname === "/health") return json({ ok: true, service: "online-flipper-pairing", protocol: 2 });
    if (url.pathname === "/api/sessions" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { duration?: string } | null;
      if (!body || body.duration !== "24h") return json({ error: "Only 24-hour sessions are supported" }, 400);
      const id = randomToken(12);
      const ownerToken = randomToken(32);
      const joinCode = randomToken(16);
      const stub = env.SESSIONS.get(env.SESSIONS.idFromName(id));
      const response = await stub.fetch("https://session.internal/create", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ ownerHash: await digest(ownerToken), joinCodeHash: await digest(joinCode) }),
      });
      if (!response.ok) return json({ error: "Could not create session" }, 500);
      return json({
        sessionId: id,
        ownerToken,
        qrPayload: JSON.stringify({ v: 1, service: "online-flipper-zero", sessionId: id, joinCode }),
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
  private sockets = new Map<WebSocket, "owner" | "client">();

  constructor(private state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/create" && request.method === "POST") {
      const input = await request.json() as { ownerHash: string; joinCodeHash: string };
      const now = Date.now();
      const session: Session = {
        ownerHash: input.ownerHash,
        joinCodeHash: input.joinCodeHash,
        createdAt: now,
        expiresAt: now + 86400000,
        pending: null,
        clientHash: null,
        revoked: false,
      };
      await this.state.storage.put("session", session);
      await this.state.storage.setAlarm(session.expiresAt!);
      return json({ ok: true });
    }

    const session = await this.state.storage.get<Session>("session");
    if (!session || session.revoked) return json({ error: "session unavailable" }, 404);
    if (session.expiresAt && Date.now() >= session.expiresAt) {
      await this.revoke(session);
      return json({ error: "session expired" }, 410);
    }

    if (url.pathname === "/join" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { joinCode?: string; deviceName?: string } | null;
      if (!body?.joinCode || !session.joinCodeHash || await digest(body.joinCode) !== session.joinCodeHash) {
        return json({ error: "invalid QR invitation" }, 401);
      }
      if (session.clientHash || session.pending) return json({ error: "invitation already used" }, 409);
      const requestToken = randomToken(32);
      const pending: PendingJoin = {
        requestId: randomToken(8),
        requestHash: await digest(requestToken),
        deviceName: (body.deviceName || "Android device").slice(0, 48),
      };
      await this.state.storage.put("session", { ...session, joinCodeHash: null, pending });
      return json({ requestId: pending.requestId, requestToken, status: "waiting_for_owner" }, 202);
    }

    if (url.pathname === "/join/status" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { requestToken?: string } | null;
      if (!body?.requestToken) return json({ error: "invalid join request" }, 401);
      const hash = await digest(body.requestToken);
      if (session.pending && hash === session.pending.requestHash) return json({ status: "waiting_for_owner" });
      if (session.approvedRequestHash && hash === session.approvedRequestHash && session.clientToken) {
        return json({ status: "approved", accessToken: session.clientToken });
      }
      return json({ error: "join request expired or denied" }, 410);
    }

    if (url.pathname === "/status" && request.method === "GET") {
      const supplied = request.headers.get("x-owner-token") || "";
      if (!supplied || await digest(supplied) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      return json({
        status: session.clientHash ? "paired" : session.pending ? "approval_pending" : "waiting",
        pendingRequest: session.pending ? { requestId: session.pending.requestId, deviceName: session.pending.deviceName } : null,
        expiresAt: session.expiresAt,
      });
    }

    if (url.pathname === "/approve" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { ownerToken?: string; requestId?: string } | null;
      if (!body?.ownerToken || await digest(body.ownerToken) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      if (!session.pending || body.requestId !== session.pending.requestId) return json({ error: "pending request not found" }, 404);
      const accessToken = randomToken(32);
      const updated: Session = {
        ...session,
        clientToken: accessToken,
        clientHash: await digest(accessToken),
        approvedRequestHash: session.pending.requestHash,
        pending: null,
      };
      await this.state.storage.put("session", updated);
      return json({ status: "paired", deviceName: session.pending.deviceName });
    }

    if (url.pathname === "/deny" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { ownerToken?: string; requestId?: string } | null;
      if (!body?.ownerToken || await digest(body.ownerToken) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      if (!session.pending || body.requestId !== session.pending.requestId) return json({ error: "pending request not found" }, 404);
      await this.state.storage.put("session", { ...session, pending: null, joinCodeHash: null });
      return json({ status: "denied" });
    }

    if (url.pathname === "/revoke" && request.method === "POST") {
      const body = await request.json().catch(() => null) as { ownerToken?: string } | null;
      if (!body?.ownerToken || await digest(body.ownerToken) !== session.ownerHash) return json({ error: "owner authorization required" }, 401);
      await this.revoke(session);
      return json({ status: "revoked" });
    }

    if (url.pathname === "/ws" && request.headers.get("Upgrade") === "websocket") {
      const token = url.searchParams.get("token") || "";
      const tokenHash = await digest(token);
      const role: "owner" | "client" | null =
        tokenHash === session.ownerHash ? "owner" :
        session.clientHash && tokenHash === session.clientHash ? "client" : null;
      if (!role) return json({ error: "device authorization required" }, 401);
      const pair = new WebSocketPair();
      const client = pair[0];
      const server = pair[1];
      server.accept();
      this.sockets.set(server, role);
      server.addEventListener("close", () => this.sockets.delete(server));
      server.addEventListener("message", event => {
        if (typeof event.data !== "string" || event.data.length > 2048) return;
        try {
          const message = JSON.parse(event.data) as { type?: string; id?: string; action?: string; payload?: unknown };
          if (!message || typeof message.type !== "string") return;
          if (role === "owner" && message.type === "command") {
            const allowed = ["get_status", "vibrate", "ring", "stop_ring"];
            if (!message.id || typeof message.id !== "string" || !message.id.match(/^[a-f0-9-]{8,64}$/i)) return;
            if (!message.action || !allowed.includes(message.action)) return;
            this.sendToRole("client", JSON.stringify({ type: "command", id: message.id, action: message.action }));
          } else if (role === "client" && (message.type === "result" || message.type === "device_status")) {
            const safe = JSON.stringify({
              type: message.type,
              id: typeof message.id === "string" ? message.id : null,
              payload: this.safePayload(message.payload),
              at: Date.now(),
            });
            this.sendToRole("owner", safe);
          }
        } catch { /* Ignore malformed frames. */ }
      });
      return new Response(null, { status: 101, webSocket: client });
    }
    return json({ error: "not found" }, 404);
  }

  private safePayload(value: unknown): unknown {
    if (!value || typeof value !== "object") return null;
    const input = value as Record<string, unknown>;
    const output: Record<string, string | number | boolean | null> = {};
    for (const key of ["batteryPercent", "charging", "androidVersion", "manufacturer", "model", "freeStorageMb", "message", "ok"]) {
      const item = input[key];
      if (typeof item === "string" || typeof item === "number" || typeof item === "boolean" || item === null) output[key] = item;
    }
    return output;
  }

  private sendToRole(role: "owner" | "client", data: string): void {
    for (const [socket, socketRole] of this.sockets) {
      if (socketRole === role && socket.readyState === WebSocket.OPEN) socket.send(data);
    }
  }

  private async revoke(session: Session): Promise<void> {
    await this.state.storage.put("session", { ...session, revoked: true, clientHash: null, clientToken: null, pending: null, joinCodeHash: null });
    for (const socket of this.sockets.keys()) socket.close(1000, "Session revoked");
    this.sockets.clear();
  }

  async alarm(): Promise<void> {
    const session = await this.state.storage.get<Session>("session");
    if (session && !session.revoked && session.expiresAt && Date.now() >= session.expiresAt) await this.revoke(session);
  }
}

import { createAdminClient } from "../_shared/supabase.ts";
import { err, ok, optionsResponse } from "../_shared/response.ts";

type Admin = ReturnType<typeof createAdminClient>;
type Row = Record<string, unknown>;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  try {
    const body = await req.json();
    switch (body.action) {
      case "login":
        return await handleLogin(createAdminClient(), body);
      default:
        return err(`Unknown action: ${String(body.action ?? "")}`);
    }
  } catch (e) {
    console.error("auth error:", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});

async function handleLogin(admin: Admin, body: Row) {
  const userId = nonEmptyString(body.user_id);
  const password = nonEmptyString(body.password);
  if (!userId || !password) {
    return err("user_id and password are required", 400);
  }

  const { data: profile, error: profileError } = await admin
    .from("profiles")
    .select("id,user_id,name,user_role,phone_num,email,profile_image_url,group_id,invite_code,is_invite_checked")
    .eq("user_id", userId)
    .maybeSingle();

  if (profileError || !profile) {
    return err("Invalid credentials", 401);
  }

  const profileRow = profile as Row;
  const email = nonEmptyString(profileRow.email);
  const profileAuthId = nonEmptyString(profileRow.id);
  if (!email || !profileAuthId) {
    return err("Supabase auth profile is incomplete", 401);
  }

  const session = await signInWithPassword(email, password);
  const accessToken = nonEmptyString(session?.access_token);
  if (!session || !accessToken || nonEmptyString(session.user?.id) !== profileAuthId) {
    return err("Invalid credentials", 401);
  }

  return ok({
    message: "로그인 성공",
    access_token: accessToken,
    auth_token: accessToken,
    token: accessToken,
    refresh_token: session.refresh_token,
    user: {
      user_id: profileRow.user_id,
      name: profileRow.name,
      user_role: profileRow.user_role,
      phone_num: profileRow.phone_num,
      email: profileRow.email,
      profile_image_url: profileRow.profile_image_url,
      group_id: profileRow.group_id == null ? null : String(profileRow.group_id),
      invite_code: profileRow.invite_code,
      is_invite_checked: Boolean(profileRow.is_invite_checked),
    },
  });
}

async function signInWithPassword(email: string, password: string) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !anonKey) return null;

  const response = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: {
      apikey: anonKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) return null;
  return await response.json() as {
    access_token: string;
    refresh_token?: string;
    user?: { id?: string };
  };
}

function nonEmptyString(value: unknown) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

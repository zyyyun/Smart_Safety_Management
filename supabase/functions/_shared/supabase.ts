import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// 사용자 JWT 기반 클라이언트 (RLS 적용)
export function createUserClient(req: Request) {
  const authHeader = req.headers.get("Authorization")!;
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    {
      global: { headers: { Authorization: authHeader } },
    }
  );
}

// 서비스 롤 클라이언트 (RLS 우회, 관리 작업용)
export function createAdminClient() {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } }
  );
}

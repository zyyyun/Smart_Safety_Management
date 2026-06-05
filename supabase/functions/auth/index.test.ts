Deno.test("auth login returns Supabase access token fields and existing user payload", async () => {
  const text = await Deno.readTextFile(new URL("./index.ts", import.meta.url));

  assertIncludes(text, 'case "login":');
  assertIncludes(text, "/auth/v1/token?grant_type=password");
  assertIncludes(text, "const accessToken = nonEmptyString(session?.access_token);");
  assertIncludes(text, "access_token: accessToken");
  assertIncludes(text, "auth_token: accessToken");
  assertIncludes(text, "token: accessToken");
  assertIncludes(text, "user: {");
  assertIncludes(text, "user_id: profileRow.user_id");
});

Deno.test("auth login binds Supabase auth user id to profile id", async () => {
  const text = await Deno.readTextFile(new URL("./index.ts", import.meta.url));

  assertIncludes(text, 'select("id,user_id,name,user_role,phone_num,email,profile_image_url,group_id,invite_code,is_invite_checked")');
  assertIncludes(text, "const profileAuthId = nonEmptyString(profileRow.id);");
  assertIncludes(text, "nonEmptyString(session.user?.id) !== profileAuthId");
});

function assertIncludes(actual: string, expected: string) {
  if (!actual.includes(expected)) {
    throw new Error(`Expected source to contain ${expected}`);
  }
}

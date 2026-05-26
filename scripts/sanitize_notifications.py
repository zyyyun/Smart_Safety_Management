# scripts/sanitize_notifications.py
# Phase 12 fix — remove mojibake from supabase/functions/notifications/index.ts so Deno parser passes.
# Replaces all non-ASCII string literals + comments with ASCII placeholders.

import re
import sys

PATH = "supabase/functions/notifications/index.ts"


def replace_in_strings(s: str) -> str:
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"' or c == "'":
            end = i + 1
            while end < len(s):
                if s[end] == "\\" and end + 1 < len(s):
                    end += 2
                    continue
                if s[end] == c:
                    break
                end += 1
            content = s[i + 1 : end]
            if any(ord(ch) > 127 or ch == "�" for ch in content):
                out.append(c + "msg" + c)
            else:
                out.append(s[i : end + 1])
            i = end + 1
        elif c == "`":
            end = i + 1
            while end < len(s):
                if s[end] == "`":
                    break
                if s[end] == "\\":
                    end += 2
                    continue
                end += 1
            content = s[i + 1 : end]
            if any(ord(ch) > 127 or ch == "�" for ch in content):
                placeholders = re.findall(r"\$\{[^}]+\}", content)
                cleaned = "msg " + " ".join(placeholders) if placeholders else "msg"
                out.append("`" + cleaned + "`")
            else:
                out.append(s[i : end + 1])
            i = end + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def main():
    with open(PATH, "rb") as f:
        raw = f.read()

    text = raw.decode("utf-8", errors="replace")
    lines = text.split("\n")

    fixed = []
    for line in lines:
        if not any(ord(c) > 127 or c == "�" for c in line):
            fixed.append(line)
            continue
        stripped = line.lstrip()
        indent = line[: len(line) - len(stripped)]
        if stripped.startswith("//"):
            fixed.append(indent + "// (comment redacted: non-ASCII)")
            continue
        cleaned = replace_in_strings(line)
        if "//" in cleaned:
            idx = cleaned.find("//")
            before = cleaned[:idx]
            after = cleaned[idx + 2 :]
            if any(ord(c) > 127 or c == "�" for c in after):
                cleaned = before + "// (inline comment redacted)"
        fixed.append(cleaned)

    new_text = "\n".join(fixed)
    bad = sum(1 for c in new_text if ord(c) > 127 or c == "�")
    print(f"After sanitize: bad chars remaining = {bad}")

    with open(PATH, "w", encoding="utf-8", newline="") as f:
        f.write(new_text)
    print("Saved.")


if __name__ == "__main__":
    main()

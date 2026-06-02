from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
EDGE_AI_EVENTS = REPO_ROOT / "supabase" / "functions" / "_shared" / "ai_events.ts"
DETAIL_ROUTE = REPO_ROOT / "server" / "get_detection_event_detail.js"
RECENT_ROUTE = REPO_ROOT / "server" / "get_recent_detection_events.js"
LIST_ROUTE = REPO_ROOT / "server" / "get_detection_events.js"
DETECTION_EDGE_FUNCTION = REPO_ROOT / "supabase" / "functions" / "detection" / "index.ts"


def test_edge_ai_event_prefers_live_url_detail_and_refreshes_duplicate_evidence():
    src = EDGE_AI_EVENTS.read_text(encoding="utf-8")

    assert "live_url_detail" in src
    assert "eventLiveUrl" in src
    assert "live_url: eventLiveUrl" in src

    dup_start = src.index("if (!dupErr && recent && recent.length > 0)")
    dup_end = src.index("// 3. camera_captures row")
    duplicate_src = src[dup_start:dup_end]

    assert ".from(\"camera_captures\")" in duplicate_src
    assert "captureImageUrl" in duplicate_src
    assert ".from(\"detection_events\")" in duplicate_src
    assert "capture_id" in duplicate_src
    assert "live_url: eventLiveUrl" in duplicate_src
    assert "detected_at" in duplicate_src


def test_node_detail_route_uses_event_capture_and_current_live_url_detail():
    src = DETAIL_ROUTE.read_text(encoding="utf-8")

    assert "COALESCE(NULLIF(c.live_url_detail, '')" in src
    assert "NULLIF(de.live_url, '')" in src
    assert "as live_url" in src
    assert "fallback_capture" in src
    assert "COALESCE(cc.image_url, fallback_capture.image_url) as capture_image_url" in src
    assert "COALESCE(cc.capture_id, fallback_capture.capture_id) as capture_id" in src


def test_node_event_lists_include_capture_image_url_for_thumbnails():
    for route in (RECENT_ROUTE, LIST_ROUTE):
        src = route.read_text(encoding="utf-8")
        assert "LEFT JOIN camera_captures cc ON de.capture_id = cc.capture_id" in src
        assert "cc.image_url as image_url" in src


def test_detection_edge_function_serves_event_capture_media_contract():
    src = DETECTION_EDGE_FUNCTION.read_text(encoding="utf-8")

    assert 'case "event_detail"' in src
    assert 'case "recent_events"' in src
    assert 'case "events"' in src
    assert ".from(\"camera_captures\")" in src
    assert "capture_image_url" in src
    assert "image_url" in src
    assert "live_url_detail" in src

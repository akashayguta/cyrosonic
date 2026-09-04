import express from 'express';
import cors from 'cors';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { Innertube, UniversalCache } from 'youtubei.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const port = process.env.PORT || 5000;
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// --- Broadcast Storage & Persistence ---
const BROADCAST_FILE = path.join(__dirname, 'broadcasts.json');
function loadBroadcasts() {
    try {
        if (fs.existsSync(BROADCAST_FILE)) {
            return JSON.parse(fs.readFileSync(BROADCAST_FILE, 'utf-8'));
        }
    } catch (_) {}
    return [
        {
            id: "bc_welcome",
            title: "⚡ Welcome to CyroSonic",
            message: "Stream lossless music, enjoy 3-phase speed dials, and explore smart taste radio.",
            trackQuery: "Blinding Lights The Weeknd",
            trackId: "4NR4vK2ZlA",
            trackUrl: "https://cyrosonic.com",
            imageUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800",
            actionText: "▶️ Listen Now",
            timestamp: Date.now()
        }
    ];
}

function saveBroadcasts(list) {
    try {
        fs.writeFileSync(BROADCAST_FILE, JSON.stringify(list, null, 2), 'utf-8');
    } catch (e) {
        console.error("Failed to save broadcasts:", e.message);
    }
}
let broadcasts = loadBroadcasts();

app.get('/', (req, res) => {
    res.json({
        status: "online",
        service: "CyroSonic Production API",
        version: "2.0.0",
        domain: "cyrosonic.com",
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

// --- In-App Over-The-Air (OTA) Updates & Telematics Endpoint ---
app.get('/api/version', (req, res) => {
    res.json({
        versionCode: 16,
        versionName: "2.0.0",
        apkUrl: "https://cyrosonic.com/download/CyroSonic-Release.apk",
        changelog: "• YouTube Music 3-Phase Speed Dial (Most Listened, Recommended, Related)\n• Over-The-Air (OTA) In-App APK Updates\n• Non-linear Taste Radio recommendation playback\n• Pre-cached instant audio start\n• Dynamic Vibe visual themes on refresh\n• Universal cyrosonic.com music share links\n• Cloud Server-Driven Swiggy-Style Broadcasts",
        forceUpdate: false,
        featureFlags: {
            enableHighResAudio: true,
            enableTasteRadio: true,
            enableOtaEqualizer: true,
            bassBoostDb: 1.5
        }
    });
});

// --- Server-Driven Broadcast API (Swiggy / Zomato Style) ---
app.get('/api/broadcast/latest', (req, res) => {
    res.json({
        success: true,
        broadcast: broadcasts[0] || null
    });
});

app.get('/api/broadcasts', (req, res) => {
    res.json({
        success: true,
        broadcasts
    });
});

app.post('/api/broadcast', (req, res) => {
    const { title, message, trackQuery, trackId, imageUrl, actionText, adminKey } = req.body;
    const expectedKey = process.env.ADMIN_KEY || 'cyrosonic2026';
    if (adminKey !== expectedKey && req.headers['x-admin-key'] !== expectedKey) {
        return res.status(401).json({ success: false, error: "Unauthorized: Invalid Admin Key" });
    }
    if (!title || !message) {
        return res.status(400).json({ success: false, error: "Title and message are required" });
    }
    const newBc = {
        id: `bc_${Date.now()}`,
        title: title.trim(),
        message: message.trim(),
        trackQuery: (trackQuery || trackId || '').trim(),
        trackId: (trackId || '').trim(),
        trackUrl: trackId ? `https://cyrosonic.com/track/${trackId}` : 'https://cyrosonic.com',
        imageUrl: (imageUrl || '').trim(),
        actionText: (actionText || '▶️ Listen Now').trim(),
        timestamp: Date.now()
    };
    broadcasts.unshift(newBc);
    if (broadcasts.length > 50) broadcasts = broadcasts.slice(0, 50);
    saveBroadcasts(broadcasts);
    console.log(`[Broadcast Dispatched] "${newBc.title}" to all CyroSonic devices.`);
    res.json({
        success: true,
        message: "Broadcast published successfully to all CyroSonic devices!",
        broadcast: newBc
    });
});

// --- Web Admin Broadcast Dashboard (Swiggy Style Cloud Console) ---
app.get('/admin', (req, res) => {
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CyroSonic Admin • Broadcast Console</title>
    <link rel="icon" href="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background: #080B11;
            color: #E2E8F0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            min-height: 100vh;
            padding: 30px 20px;
        }
        .container { max-width: 1100px; margin: 0 auto; }
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 28px;
            padding-bottom: 18px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        }
        .logo { font-size: 24px; font-weight: 800; background: linear-gradient(135deg, #00F2FE, #4FACFE); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        .tag { font-size: 11px; font-weight: 700; background: rgba(0, 242, 254, 0.15); color: #00F2FE; padding: 4px 10px; border-radius: 20px; border: 1px solid rgba(0, 242, 254, 0.3); }
        .grid { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 24px; }
        @media (max-width: 850px) { .grid { grid-template-columns: 1fr; } }
        .card {
            background: rgba(18, 24, 38, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 18px;
            padding: 24px;
            backdrop-filter: blur(16px);
        }
        h2 { font-size: 18px; font-weight: 700; margin-bottom: 16px; color: #FFF; display: flex; align-items: center; gap: 8px; }
        .form-group { margin-bottom: 16px; }
        label { display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.6px; color: #94A3B8; margin-bottom: 6px; }
        input, textarea {
            width: 100%;
            background: rgba(10, 14, 23, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.12);
            border-radius: 12px;
            padding: 12px 14px;
            color: #FFF;
            font-size: 14px;
            outline: none;
            transition: border-color 0.2s;
        }
        input:focus, textarea:focus { border-color: #00F2FE; }
        textarea { resize: vertical; min-height: 80px; }
        .btn-broadcast {
            width: 100%;
            background: linear-gradient(135deg, #00F2FE, #0072FF);
            color: #000;
            border: none;
            border-radius: 12px;
            padding: 14px;
            font-size: 15px;
            font-weight: 800;
            cursor: pointer;
            transition: transform 0.15s, opacity 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .btn-broadcast:hover { transform: translateY(-2px); }
        .btn-broadcast:active { transform: translateY(0); }
        /* Live Phone Notification Mockup */
        .phone-mockup {
            background: #000;
            border-radius: 28px;
            padding: 16px;
            border: 2px solid rgba(255, 255, 255, 0.15);
            box-shadow: 0 20px 40px rgba(0,0,0,0.6);
        }
        .notif-card {
            background: #1E2538;
            border-radius: 16px;
            padding: 14px;
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .notif-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
        .notif-icon { width: 18px; height: 18px; border-radius: 4px; background: #00F2FE; display: flex; align-items: center; justify-content: center; font-size: 10px; color: #000; font-weight: 900; }
        .notif-app-name { font-size: 12px; font-weight: 600; color: #94A3B8; }
        .notif-time { font-size: 11px; color: #64748B; margin-left: auto; }
        .notif-title { font-size: 14px; font-weight: 700; color: #FFF; margin-bottom: 4px; }
        .notif-body { font-size: 12.5px; color: #CBD5E1; line-height: 1.4; margin-bottom: 10px; }
        .notif-banner { width: 100%; height: 120px; border-radius: 10px; object-fit: cover; margin-bottom: 10px; display: block; }
        .notif-action {
            display: inline-block;
            background: rgba(0, 242, 254, 0.15);
            color: #00F2FE;
            font-size: 12px;
            font-weight: 700;
            padding: 6px 12px;
            border-radius: 8px;
            border: 1px solid rgba(0, 242, 254, 0.3);
        }
        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: #10B981;
            color: #000;
            font-weight: 700;
            padding: 12px 20px;
            border-radius: 10px;
            display: none;
            box-shadow: 0 10px 25px rgba(0,0,0,0.4);
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div>
                <div class="logo">⚡ CYROSONIC</div>
                <div style="font-size: 13px; color: #64748B; margin-top: 2px;">Server Push & Broadcast Center</div>
            </div>
            <span class="tag">Swiggy-Style Cloud Push</span>
        </header>

        <div class="grid">
            <!-- Left: Broadcast Composer -->
            <div class="card">
                <h2>📢 Compose Broadcast Push</h2>
                <form id="bcForm">
                    <div class="form-group">
                        <label>Notification Title</label>
                        <input id="inTitle" type="text" value="🔥 Midnight Drops are Live!" placeholder="e.g. Weekend Vibe Drop" required>
                    </div>
                    <div class="form-group">
                        <label>Notification Message</label>
                        <textarea id="inMsg" placeholder="e.g. Tap to stream the exclusive weekend mixtape on CyroSonic!" required>Tap to stream the exclusive new releases on CyroSonic with lossless clarity!</textarea>
                    </div>
                    <div class="form-group">
                        <label>Target Track / Song Query (Auto-plays on tap)</label>
                        <input id="inTrack" type="text" value="Blinding Lights The Weeknd" placeholder="Song title, artist, or YouTube ID">
                    </div>
                    <div class="form-group">
                        <label>Banner Image URL (Optional)</label>
                        <input id="inImg" type="url" value="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800" placeholder="https://image-url.com/banner.jpg">
                    </div>
                    <div class="form-group">
                        <label>Action Button Label</label>
                        <input id="inAction" type="text" value="▶️ Listen Now" placeholder="e.g. ▶️ Listen Now">
                    </div>
                    <div class="form-group">
                        <label>Admin Passkey</label>
                        <input id="inKey" type="password" value="cyrosonic2026" placeholder="Admin Secret">
                    </div>
                    <button type="submit" class="btn-broadcast">🚀 Dispatch Broadcast to All Users</button>
                </form>
            </div>

            <!-- Right: Live Android Notification Simulation -->
            <div>
                <div class="card">
                    <h2>📱 Real-Time Phone Preview</h2>
                    <p style="font-size: 12px; color: #64748B; margin-bottom: 16px;">This simulates how users receive your broadcast on Android 14:</p>
                    <div class="phone-mockup">
                        <div class="notif-card">
                            <div class="notif-header">
                                <div class="notif-icon">🎵</div>
                                <span class="notif-app-name">CyroSonic</span>
                                <span class="notif-time">now</span>
                            </div>
                            <div class="notif-title" id="pvTitle">🔥 Midnight Drops are Live!</div>
                            <div class="notif-body" id="pvMsg">Tap to stream the exclusive new releases on CyroSonic with lossless clarity!</div>
                            <img class="notif-banner" id="pvImg" src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800" alt="Banner Preview">
                            <div class="notif-action" id="pvAction">▶️ Listen Now</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="toast" id="toast">✅ Broadcast sent successfully to all CyroSonic devices!</div>

    <script>
        const inTitle = document.getElementById('inTitle');
        const inMsg = document.getElementById('inMsg');
        const inImg = document.getElementById('inImg');
        const inAction = document.getElementById('inAction');
        const inKey = document.getElementById('inKey');
        const inTrack = document.getElementById('inTrack');

        const pvTitle = document.getElementById('pvTitle');
        const pvMsg = document.getElementById('pvMsg');
        const pvImg = document.getElementById('pvImg');
        const pvAction = document.getElementById('pvAction');
        const toast = document.getElementById('toast');

        function updatePreview() {
            pvTitle.textContent = inTitle.value || 'CyroSonic';
            pvMsg.textContent = inMsg.value || 'New music is available now.';
            pvAction.textContent = inAction.value || '▶️ Listen Now';
            if (inImg.value) {
                pvImg.src = inImg.value;
                pvImg.style.display = 'block';
            } else {
                pvImg.style.display = 'none';
            }
        }

        inTitle.addEventListener('input', updatePreview);
        inMsg.addEventListener('input', updatePreview);
        inImg.addEventListener('input', updatePreview);
        inAction.addEventListener('input', updatePreview);

        document.getElementById('bcForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                title: inTitle.value,
                message: inMsg.value,
                trackQuery: inTrack.value,
                imageUrl: inImg.value,
                actionText: inAction.value,
                adminKey: inKey.value
            };
            try {
                const res = await fetch('/api/broadcast', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                if (data.success) {
                    toast.textContent = '🚀 Broadcast successfully dispatched to all devices!';
                    toast.style.background = '#10B981';
                } else {
                    toast.textContent = '❌ Error: ' + (data.error || 'Failed');
                    toast.style.background = '#EF4444';
                }
                toast.style.display = 'block';
                setTimeout(() => toast.style.display = 'none', 4000);
            } catch (err) {
                toast.textContent = '❌ Network error: ' + err.message;
                toast.style.background = '#EF4444';
                toast.style.display = 'block';
                setTimeout(() => toast.style.display = 'none', 4000);
            }
        });
    </script>
</body>
</html>`);
});

// --- Public APK Direct Download Endpoint ---
app.get('/download/:filename?', (req, res) => {
    const filename = req.params.filename || 'CyroSonic-Release.apk';
    const possiblePaths = [
        path.join(__dirname, 'CyroSonic-Release.apk'),
        path.join(__dirname, '..', 'CyroSonic-Release.apk'),
        path.join(__dirname, '..', 'HunterXMusic-Release.apk'),
        path.join(__dirname, '..', 'HunterXMusic.apk'),
        path.join(__dirname, 'public', filename)
    ];
    for (const p of possiblePaths) {
        if (fs.existsSync(p)) {
            return res.download(p, 'CyroSonic-Release.apk');
        }
    }
    res.status(404).send("CyroSonic APK release build is being compiled. Please check back shortly.");
});

// --- Shareable Music Links Landing Page & Deep Link Bridge ---
app.get('/track/:id', async (req, res) => {
    const trackId = req.params.id;
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Listen on CyroSonic</title>
    <meta property="og:title" content="Play on CyroSonic">
    <meta property="og:description" content="Stream lossless music with smart taste radio on CyroSonic.">
    <meta property="og:url" content="https://cyrosonic.com/track/${trackId}">
    <link rel="icon" href="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100">
    <style>
        body {
            margin: 0;
            background: #090D16;
            color: #FFFFFF;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
            box-sizing: border-box;
        }
        .card {
            background: rgba(255, 255, 255, 0.05);
            backdrop-filter: blur(20px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 24px;
            padding: 32px;
            max-width: 420px;
            width: 100%;
            text-align: center;
            box-shadow: 0 20px 40px rgba(0,0,0,0.6);
        }
        .cover {
            width: 180px;
            height: 180px;
            border-radius: 20px;
            margin: 0 auto 20px;
            object-fit: cover;
            box-shadow: 0 10px 30px rgba(6, 182, 212, 0.25);
        }
        h1 { font-size: 22px; font-weight: 800; margin: 0 0 8px; }
        p { color: #94A3B8; font-size: 14px; margin: 0 0 24px; }
        .btn {
            display: block;
            width: 100%;
            padding: 14px;
            margin-bottom: 12px;
            border-radius: 14px;
            font-size: 15px;
            font-weight: 700;
            text-decoration: none;
            box-sizing: border-box;
            transition: transform 0.15s ease;
        }
        .btn-primary { background: linear-gradient(135deg, #06B6D4, #3B82F6); color: #000; }
        .btn-secondary { background: rgba(255, 255, 255, 0.1); color: #FFF; border: 1px solid rgba(255, 255, 255, 0.15); }
        .btn:hover { transform: scale(1.02); }
    </style>
</head>
<body>
    <div class="card">
        <img class="cover" src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500" alt="Album Art">
        <h1>CyroSonic Music</h1>
        <p>Listen to this track with non-linear smart taste radio in lossless audio.</p>
        <a class="btn btn-primary" href="cyrosonic://track/${trackId}">Open in CyroSonic App</a>
        <a class="btn btn-secondary" href="https://cyrosonic.com/download/CyroSonic-Release.apk">Download Android APK</a>
    </div>
    <script>
        window.location.href = "cyrosonic://track/${trackId}";
    </script>
</body>
</html>`);
});
let yt = null;
async function initYouTube() {
    try {
        yt = await Innertube.create({
            cache: new UniversalCache(false),
            generate_session_locally: true
        });
        console.log("CyroSonic YouTube InnerTube engine initialized successfully.");
    }
    catch (e) {
        console.error("YouTube InnerTube init error:", e.message);
    }
}
initYouTube();
// --- AI Companion Endpoint ---
app.get('/ai/gpt-5', async (req, res) => {
    try {
        const text = (req.query.text || '').trim();
        if (!text) {
            return res.json({
                status: true,
                statusCode: 200,
                creator: "CyroSonic Local TS Server",
                model: "gpt-5",
                text: "Hey! Ask me anything about music, songs, or artists!",
                note: "Music AI Local Server"
            });
        }
        // The old `?unlocked=true` query param was a fake gate — any caller
        // could flip it, and the app client always did. The client owns its
        // own prompt policy now; this server simply proxies the text through.
        const finalPrompt = text;
        const encodedPrompt = encodeURIComponent(finalPrompt);
        const url = `https://apis.prexzyvilla.site/ai/gpt-5?text=${encodedPrompt}`;
        // Hard 15s cap on the upstream call — previously a hung upstream
        // would hang the client for its full 30s read timeout.
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 15_000);
        try {
            const apiResponse = await fetch(url, { signal: controller.signal });
            if (!apiResponse.ok) {
                throw new Error(`Upstream server returned error: ${apiResponse.status}`);
            }
            const data = await apiResponse.json();
            const responseJson = {
                status: true,
                statusCode: 200,
                creator: "CyroSonic Local TS Server",
                model: "gpt-5-music-expert",
                text: data.text || "I couldn't process that query. Let's talk about songs or artists instead!",
                note: "Enhanced local TS music context wrapper"
            };
            res.json(responseJson);
        }
        finally {
            clearTimeout(timer);
        }
    }
    catch (error) {
        console.error("AI Server Error:", error.message);
        // Real failure code — the old handler masked every error as a fake
        // success, so the app could never tell "server says no" from a win.
        res.status(502).json({
            status: false,
            statusCode: 502,
            creator: "CyroSonic Local TS Server",
            model: "gpt-5-music-expert-fallback",
            text: "The AI brain is unreachable right now.",
            note: "Upstream unavailable"
        });
    }
});
// --- YouTube Music Re-Engineered Search Endpoint ---
app.get('/youtube/search', async (req, res) => {
    try {
        const q = (req.query.q || '').trim();
        if (!q)
            return res.json({ success: true, results: [] });
        if (!yt)
            yt = await Innertube.create({ cache: new UniversalCache(false) });
        const search = await yt.music.search(q, { type: 'song' });
        const songs = search.contents?.flatMap((c) => c.contents || []) || [];
        const formatted = songs.map((s) => ({
            id: s.id || s.video_id,
            title: s.title?.text || s.title || 'Unknown Title',
            artist: s.artists?.map((a) => a.name).join(', ') || s.author?.name || 'Unknown Artist',
            album: s.album?.name || '',
            duration: s.duration?.seconds || 180,
            albumArt: s.thumbnails?.[s.thumbnails.length - 1]?.url || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
            streamUrl: ''
        })).filter((t) => t.id);
        res.json({ success: true, results: formatted });
    }
    catch (error) {
        console.error("YouTube Search Error:", error.message);
        res.json({ success: false, results: [], error: error.message });
    }
});
// --- YouTube Audio Stream Decryption Endpoint ---
app.get('/youtube/stream', async (req, res) => {
    try {
        const videoId = (req.query.id || '').trim();
        if (!videoId)
            return res.status(400).json({ error: "Missing video ID" });
        if (!yt)
            yt = await Innertube.create({ cache: new UniversalCache(false) });
        const info = await yt.getBasicInfo(videoId, { client: 'ANDROID_VR' });
        const streamingData = info.streaming_data;
        const adaptiveFormats = streamingData?.adaptive_formats || [];
        const audioFormats = adaptiveFormats
            .filter((f) => f.mime_type?.startsWith('audio/'))
            .sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));
        const bestUrl = audioFormats[0]?.url || audioFormats[0]?.decipher?.(yt.session.player) || null;
        res.json({
            success: !!bestUrl,
            videoId,
            url: bestUrl,
            bitrate: audioFormats[0]?.bitrate || 0
        });
    }
    catch (error) {
        console.error("YouTube Stream Error:", error.message);
        res.json({ success: false, error: error.message });
    }
});
app.listen(port, () => {
    console.log(`CyroSonic TS AI & YouTube Server listening at http://localhost:${port}`);
});

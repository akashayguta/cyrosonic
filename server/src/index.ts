import express from 'express';
import cors from 'cors';
import path from 'path';
import fs from 'fs';
import { Innertube, UniversalCache } from 'youtubei.js';

const app = express();
const port = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// --- Broadcast Storage & Persistence ---
const BROADCAST_FILE = path.join(__dirname, '..', 'broadcasts.json');
interface BroadcastItem {
    id: string;
    title: string;
    message: string;
    trackQuery?: string;
    trackId?: string;
    trackUrl?: string;
    imageUrl?: string;
    actionText?: string;
    timestamp: number;
}

function loadBroadcasts(): BroadcastItem[] {
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

function saveBroadcasts(list: BroadcastItem[]) {
    try {
        fs.writeFileSync(BROADCAST_FILE, JSON.stringify(list, null, 2), 'utf-8');
    } catch (e: any) {
        console.error("Failed to save broadcasts:", e.message);
    }
}
let broadcasts: BroadcastItem[] = loadBroadcasts();

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
    const newBc: BroadcastItem = {
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

// --- Public APK Direct Download Endpoint ---
app.get('/download/:filename?', (req, res) => {
    const filename = req.params.filename || 'CyroSonic-Release.apk';
    const possiblePaths = [
        path.join(__dirname, '..', 'CyroSonic-Release.apk'),
        path.join(__dirname, '..', '..', 'CyroSonic-Release.apk'),
        path.join(__dirname, '..', '..', 'HunterXMusic-Release.apk'),
        path.join(__dirname, '..', '..', 'HunterXMusic.apk'),
        path.join(__dirname, '..', 'public', filename)
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

let yt: Innertube | null = null;

async function initYouTube() {
    try {
        yt = await Innertube.create({
            cache: new UniversalCache(false),
            generate_session_locally: true
        });
        console.log("CyroSonic YouTube InnerTube engine initialized successfully.");
    } catch (e: any) {
        console.error("YouTube InnerTube init error:", e.message);
    }
}
initYouTube();

interface AiResponse {
    status: boolean;
    statusCode: number;
    creator: string;
    model: string;
    text: string;
    note: string | null;
}

// --- AI Companion Endpoint ---
app.get('/ai/gpt-5', async (req, res) => {
    try {
        const text = (req.query.text as string || '').trim();

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

            const data = await apiResponse.json() as any;

            const responseJson: AiResponse = {
                status: true,
                statusCode: 200,
                creator: "CyroSonic Local TS Server",
                model: "gpt-5-music-expert",
                text: data.text || "I couldn't process that query. Let's talk about songs or artists instead!",
                note: "Enhanced local TS music context wrapper"
            };

            res.json(responseJson);
        } finally {
            clearTimeout(timer);
        }
    } catch (error: any) {
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
        const q = (req.query.q as string || '').trim();
        if (!q) return res.json({ success: true, results: [] });

        if (!yt) yt = await Innertube.create({ cache: new UniversalCache(false) });

        const search = await yt.music.search(q, { type: 'song' });
        const songs = search.contents?.flatMap((c: any) => c.contents || []) || [];

        const formatted = songs.map((s: any) => ({
            id: s.id || s.video_id,
            title: s.title?.text || s.title || 'Unknown Title',
            artist: s.artists?.map((a: any) => a.name).join(', ') || s.author?.name || 'Unknown Artist',
            album: s.album?.name || '',
            duration: s.duration?.seconds || 180,
            albumArt: s.thumbnails?.[s.thumbnails.length - 1]?.url || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
            streamUrl: ''
        })).filter((t: any) => t.id);

        res.json({ success: true, results: formatted });
    } catch (error: any) {
        console.error("YouTube Search Error:", error.message);
        res.json({ success: false, results: [], error: error.message });
    }
});

// --- YouTube Audio Stream Decryption Endpoint ---
app.get('/youtube/stream', async (req, res) => {
    try {
        const videoId = (req.query.id as string || '').trim();
        if (!videoId) return res.status(400).json({ error: "Missing video ID" });

        if (!yt) yt = await Innertube.create({ cache: new UniversalCache(false) });

        const info = await yt.getBasicInfo(videoId, { client: 'ANDROID_VR' } as any);
        const streamingData = info.streaming_data;
        const adaptiveFormats = streamingData?.adaptive_formats || [];

        const audioFormats = adaptiveFormats
            .filter((f: any) => f.mime_type?.startsWith('audio/'))
            .sort((a: any, b: any) => (b.bitrate || 0) - (a.bitrate || 0));

        const bestUrl = audioFormats[0]?.url || audioFormats[0]?.decipher?.(yt.session.player) || null;

        res.json({
            success: !!bestUrl,
            videoId,
            url: bestUrl,
            bitrate: audioFormats[0]?.bitrate || 0
        });
    } catch (error: any) {
        console.error("YouTube Stream Error:", error.message);
        res.json({ success: false, error: error.message });
    }
});

app.listen(port, () => {
    console.log(`CyroSonic TS AI & YouTube Server listening at http://localhost:${port}`);
});

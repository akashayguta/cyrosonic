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



// --- Official 3D Luxury Website & Landing Page (cyrosonic.com) ---
app.get('/', (req, res) => {
    if (req.headers.accept && req.headers.accept.includes('text/html')) {
        return res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CyroSonic • Lossless Audio & Next-Gen Music Ecosystem</title>
    <meta name="description" content="Experience master-tier music streaming with YouTube Music 3-Phase Speed Dials, millisecond-synchronized Listening Parties, and Instagram Story Lyric Cards.">
    <link rel="icon" href="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100">
    <style>
        :root {
            --primary: #00F2FE;
            --primary-glow: rgba(0, 242, 254, 0.4);
            --accent: #8B5CF6;
            --bg-dark: #05070D;
            --card-glass: rgba(18, 24, 38, 0.7);
            --border-glass: rgba(255, 255, 255, 0.1);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg-dark);
            color: #E2E8F0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            min-height: 100vh;
            overflow-x: hidden;
            perspective: 1200px;
        }
        /* Cosmic Glow Background */
        .ambient-glow {
            position: fixed;
            top: 10%;
            left: 50%;
            transform: translateX(-50%);
            width: 700px;
            height: 400px;
            background: radial-gradient(circle, rgba(0, 242, 254, 0.15) 0%, rgba(139, 92, 246, 0.12) 50%, transparent 80%);
            filter: blur(80px);
            z-index: 0;
            pointer-events: none;
        }
        nav {
            position: relative;
            z-index: 10;
            max-width: 1200px;
            margin: 0 auto;
            padding: 24px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .brand {
            font-size: 26px;
            font-weight: 900;
            letter-spacing: 0.5px;
            background: linear-gradient(135deg, #FFF, #7DD3FC, #8B5CF6);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .btn-nav-download {
            background: rgba(255, 255, 255, 0.08);
            border: 1px solid var(--border-glass);
            color: #FFF;
            padding: 10px 20px;
            border-radius: 24px;
            text-decoration: none;
            font-size: 14px;
            font-weight: 700;
            transition: all 0.2s;
        }
        .btn-nav-download:hover {
            background: var(--primary);
            color: #000;
            box-shadow: 0 0 20px var(--primary-glow);
        }
        /* Hero Section */
        .hero {
            position: relative;
            z-index: 5;
            max-width: 1200px;
            margin: 40px auto 80px;
            padding: 0 20px;
            display: grid;
            grid-template-columns: 1.1fr 0.9fr;
            gap: 40px;
            align-items: center;
        }
        @media (max-width: 900px) {
            .hero { grid-template-columns: 1fr; text-align: center; }
        }
        .badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            background: rgba(0, 242, 254, 0.12);
            border: 1px solid rgba(0, 242, 254, 0.3);
            color: var(--primary);
            border-radius: 20px;
            font-size: 12px;
            font-weight: 800;
            letter-spacing: 0.8px;
            margin-bottom: 20px;
        }
        h1 {
            font-size: 54px;
            line-height: 1.15;
            font-weight: 900;
            margin-bottom: 20px;
            background: linear-gradient(135deg, #FFFFFF 40%, #94A3B8);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        @media (max-width: 600px) { h1 { font-size: 38px; } }
        p.desc {
            font-size: 17px;
            line-height: 1.6;
            color: #94A3B8;
            margin-bottom: 32px;
            max-width: 520px;
        }
        .cta-group {
            display: flex;
            gap: 16px;
            flex-wrap: wrap;
        }
        @media (max-width: 900px) { .cta-group { justify-content: center; } }
        .btn-primary {
            background: linear-gradient(135deg, var(--primary), #0072FF);
            color: #000;
            padding: 16px 32px;
            border-radius: 16px;
            font-size: 16px;
            font-weight: 800;
            text-decoration: none;
            box-shadow: 0 10px 30px var(--primary-glow);
            transition: all 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 8px;
        }
        .btn-primary:hover {
            transform: translateY(-3px);
            box-shadow: 0 15px 40px rgba(0, 242, 254, 0.6);
        }
        /* 3D Floating Phone Showcase */
        .phone-container {
            position: relative;
            display: flex;
            justify-content: center;
            align-items: center;
        }
        .phone-3d {
            width: 320px;
            height: 640px;
            background: #000;
            border-radius: 44px;
            border: 4px solid rgba(255, 255, 255, 0.2);
            box-shadow: -20px 40px 80px rgba(0, 0, 0, 0.8), 0 0 50px rgba(0, 242, 254, 0.25);
            transform: rotateY(-12deg) rotateX(8deg);
            transition: transform 0.4s ease-out;
            padding: 14px;
            overflow: hidden;
            display: flex;
            flex-direction: column;
        }
        .phone-3d:hover {
            transform: rotateY(0deg) rotateX(0deg) scale(1.02);
        }
        .phone-notch {
            width: 110px;
            height: 22px;
            background: #111;
            border-radius: 12px;
            margin: 0 auto 14px;
        }
        .mock-album {
            width: 100%;
            height: 260px;
            border-radius: 24px;
            background: url('https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800') center/cover;
            box-shadow: 0 15px 30px rgba(0,0,0,0.6);
            margin-bottom: 20px;
        }
        .mock-track {
            font-size: 18px;
            font-weight: 800;
            color: #FFF;
            margin-bottom: 4px;
        }
        .mock-artist {
            font-size: 13px;
            color: #94A3B8;
            margin-bottom: 18px;
        }
        .mock-progress {
            width: 100%;
            height: 4px;
            background: rgba(255,255,255,0.15);
            border-radius: 2px;
            margin-bottom: 24px;
            position: relative;
        }
        .mock-progress-bar {
            width: 65%;
            height: 100%;
            background: var(--primary);
            border-radius: 2px;
        }
        .mock-controls {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0 20px;
        }
        /* Bento Grid */
        .features {
            max-width: 1200px;
            margin: 60px auto 120px;
            padding: 0 20px;
        }
        .bento-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
        }
        @media (max-width: 850px) { .bento-grid { grid-template-columns: 1fr; } }
        .bento-card {
            background: var(--card-glass);
            border: 1px solid var(--border-glass);
            border-radius: 24px;
            padding: 28px;
            backdrop-filter: blur(20px);
            transition: transform 0.2s, border-color 0.2s;
        }
        .bento-card:hover {
            transform: translateY(-4px);
            border-color: rgba(0, 242, 254, 0.4);
        }
        .bento-icon { font-size: 32px; margin-bottom: 14px; }
        .bento-title { font-size: 20px; font-weight: 800; margin-bottom: 8px; color: #FFF; }
        .bento-desc { font-size: 14px; line-height: 1.5; color: #94A3B8; }
    </style>
</head>
<body>
    <div class="ambient-glow"></div>
    <nav>
        <div class="brand">CYROSONIC</div>
        <a href="/download/CyroSonic-Release.apk" class="btn-nav-download">⬇️ Download APK</a>
    </nav>

    <main class="hero">
        <div class="hero-content">
            <div class="badge">✨ CYROSONIC v2.0 PRODUCTION RELEASE</div>
            <h1>The Master-Tier Sound Sanctuary</h1>
            <p class="desc">Experience lossless audio streaming, YouTube Music 3-Phase Speed Dials, synchronized Listening Parties, and aesthetic Story Lyric Cards on your Android device.</p>
            <div class="cta-group">
                <a href="/download/CyroSonic-Release.apk" class="btn-primary">
                    ⬇️ Download Release APK (18.8 MB)
                </a>
            </div>
        </div>

        <div class="phone-container">
            <div class="phone-3d">
                <div class="phone-notch"></div>
                <div class="mock-album"></div>
                <div class="mock-track">Celestial Horizon</div>
                <div class="mock-artist">CyroSonic Lossless Master</div>
                <div class="mock-progress">
                    <div class="mock-progress-bar"></div>
                </div>
                <div class="mock-controls">
                    <span style="font-size:24px; color:#94A3B8;">⏮️</span>
                    <span style="font-size:32px; color:var(--primary);">⏸️</span>
                    <span style="font-size:24px; color:#94A3B8;">⏭️</span>
                </div>
            </div>
        </div>
    </main>

    <section class="features">
        <div class="bento-grid">
            <div class="bento-card">
                <div class="bento-icon">⚡</div>
                <div class="bento-title">3-Phase Speed Dial</div>
                <div class="bento-desc">Instant 9-track quick picks that seed random hits on cold start and dynamically adapt to your personal listening taste.</div>
            </div>
            <div class="bento-card">
                <div class="bento-icon">🎧</div>
                <div class="bento-title">Synchronized Listening Party</div>
                <div class="bento-desc">Create 6-digit room codes to listen with friends across different cities down to the millisecond.</div>
            </div>
            <div class="bento-card">
                <div class="bento-icon">📲</div>
                <div class="bento-title">Story Lyric Card Generator</div>
                <div class="bento-desc">Long-press synced lyrics to craft Apple Music/Spotify style story cards with 1-tap direct export to Instagram & WhatsApp.</div>
            </div>
        </div>
    </section>
</body>
</html>`);
    }

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
        changelog: "• YouTube Music 3-Phase Speed Dial (Most Listened, Recommended, Related)\n• Over-The-Air (OTA) In-App APK Updates\n• Non-linear Taste Radio recommendation playback\n• Pre-cached instant audio start\n• Dynamic Vibe visual themes on refresh\n• Universal cyrosonic.com music share links\n• Shared 6-digit Listening Party Rooms\n• Instagram & WhatsApp Story Lyric Card Generator",
        forceUpdate: false,
        featureFlags: {
            enableHighResAudio: true,
            enableTasteRadio: true,
            enableOtaEqualizer: true
        }
    });
});

// --- Server-Driven Broadcast API with TTL & Design Control ---
app.get('/api/broadcast/latest', (req, res) => {
    const now = Date.now();
    // Return only active, non-expired broadcast
    const activeBroadcast = broadcasts.find(b => b.active !== false && (!b.expiresAt || b.expiresAt > now));
    res.json({
        success: true,
        broadcast: activeBroadcast || null
    });
});

app.get('/api/broadcasts', (req, res) => {
    const now = Date.now();
    const list = broadcasts.map(b => ({
        ...b,
        isExpired: Boolean(b.expiresAt && b.expiresAt <= now),
        isActive: Boolean(b.active !== false && (!b.expiresAt || b.expiresAt > now))
    }));
    res.json({
        success: true,
        broadcasts: list
    });
});

app.post('/api/broadcast', (req, res) => {
    const {
        title,
        message,
        trackQuery,
        trackId,
        imageUrl,
        actionText,
        accentColor,
        badgeText,
        styleType,
        ttlHours,
        adminKey
    } = req.body;

    const expectedKey = process.env.ADMIN_KEY;
    if (!expectedKey) {
        return res.status(500).json({ success: false, error: "ADMIN_KEY is not configured on Render server environment variables." });
    }
    if (adminKey !== expectedKey && req.headers['x-admin-key'] !== expectedKey) {
        return res.status(401).json({ success: false, error: "Unauthorized: Invalid Admin Key" });
    }
    if (!title || !message) {
        return res.status(400).json({ success: false, error: "Title and message are required" });
    }

    const hours = Number(ttlHours);
    const expiresAt = (!isNaN(hours) && hours > 0) ? (Date.now() + hours * 3600 * 1000) : null;

    const newBc = {
        id: `bc_${Date.now()}`,
        title: title.trim(),
        message: message.trim(),
        trackQuery: (trackQuery || trackId || '').trim(),
        trackId: (trackId || '').trim(),
        trackUrl: trackId ? `https://cyrosonic.com/track/${trackId}` : 'https://cyrosonic.com',
        imageUrl: (imageUrl || '').trim(),
        actionText: (actionText || '▶️ Listen Now').trim(),
        accentColor: (accentColor || '#00F2FE').trim(),
        badgeText: (badgeText || '⚡ ANNOUNCEMENT').trim(),
        styleType: (styleType || 'SERVER_BROADCAST').trim(),
        ttlHours: hours > 0 ? hours : 0,
        expiresAt: expiresAt,
        active: true,
        timestamp: Date.now()
    };

    broadcasts.unshift(newBc);
    if (broadcasts.length > 50) broadcasts = broadcasts.slice(0, 50);
    saveBroadcasts(broadcasts);
    console.log(`[Broadcast Dispatched] "${newBc.title}" (Expires in: ${hours > 0 ? hours + 'h' : 'Never'}, Color: ${newBc.accentColor})`);
    res.json({
        success: true,
        message: "Broadcast published successfully to all CyroSonic devices!",
        broadcast: newBc
    });
});

app.post('/api/broadcast/stop', (req, res) => {
    const { adminKey } = req.body;
    const expectedKey = process.env.ADMIN_KEY;
    if (!expectedKey) {
        return res.status(500).json({ success: false, error: "ADMIN_KEY is not configured on Render." });
    }
    if (adminKey !== expectedKey && req.headers['x-admin-key'] !== expectedKey) {
        return res.status(401).json({ success: false, error: "Unauthorized: Invalid Admin Key" });
    }

    let stoppedCount = 0;
    broadcasts.forEach(b => {
        if (b.active !== false) {
            b.active = false;
            stoppedCount++;
        }
    });
    saveBroadcasts(broadcasts);
    console.log(`[Broadcasts Deactivated] Stopped ${stoppedCount} active broadcasts.`);
    res.json({ success: true, message: `Successfully stopped ${stoppedCount} active broadcast(s).` });
});

app.delete('/api/broadcast/:id', (req, res) => {
    const id = req.params.id;
    const key = req.headers['x-admin-key'] || req.query.adminKey || req.body?.adminKey;
    const expectedKey = process.env.ADMIN_KEY;
    if (!expectedKey) {
        return res.status(500).json({ success: false, error: "ADMIN_KEY is not configured on Render." });
    }
    if (key !== expectedKey) {
        return res.status(401).json({ success: false, error: "Unauthorized: Invalid Admin Key" });
    }

    const index = broadcasts.findIndex(b => b.id === id);
    if (index === -1) {
        return res.status(404).json({ success: false, error: "Broadcast not found" });
    }
    broadcasts.splice(index, 1);
    saveBroadcasts(broadcasts);
    res.json({ success: true, message: "Broadcast deleted from archive." });
});

app.get('/api/admin/metrics', (req, res) => {
    res.json({
        uptimeSeconds: Math.floor(process.uptime()),
        activePartyRooms: partyRooms.size,
        totalBroadcasts: broadcasts.length,
        memoryUsageMb: Math.round(process.memoryUsage().heapUsed / 1024 / 1024),
        serverTimeUtc: new Date().toISOString()
    });
});

// Legacy /admin route disabled as requested — nobody can find it!
app.get('/admin', (req, res) => {
    res.status(404).send("Page not found");
});

// --- Secret Admin System (/adminbyhunter) & Master Studio ---
app.get('/adminbyhunter', (req, res) => {
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CyroSonic Master Admin • /adminbyhunter</title>
    <link rel="icon" href="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        :root {
            --bg: #06090E;
            --surface: rgba(15, 23, 42, 0.85);
            --border: rgba(255, 255, 255, 0.08);
            --primary: #00F2FE;
            --accent: #00F2FE;
        }
        body {
            background: var(--bg);
            color: #F8FAFC;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            min-height: 100vh;
            padding: 24px 16px 60px;
        }
        .container { max-width: 1280px; margin: 0 auto; }
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 24px;
            padding-bottom: 16px;
            border-bottom: 1px solid var(--border);
            flex-wrap: wrap;
            gap: 12px;
        }
        .brand-title {
            font-size: 24px;
            font-weight: 900;
            letter-spacing: 1px;
            background: linear-gradient(135deg, #00F2FE, #8B5CF6);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .badge-secret {
            font-size: 11px;
            font-weight: 800;
            background: rgba(0, 242, 254, 0.12);
            color: #00F2FE;
            padding: 5px 12px;
            border-radius: 20px;
            border: 1px solid rgba(0, 242, 254, 0.25);
            letter-spacing: 1px;
        }
        .metrics-bar {
            display: flex;
            gap: 16px;
            margin-bottom: 24px;
            flex-wrap: wrap;
        }
        .metric-pill {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid var(--border);
            padding: 10px 16px;
            border-radius: 12px;
            font-size: 12px;
            color: #94A3B8;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .metric-pill strong { color: #FFF; font-weight: 700; }
        .grid-layout {
            display: grid;
            grid-template-columns: 1.15fr 0.85fr;
            gap: 24px;
        }
        @media (max-width: 980px) { .grid-layout { grid-template-columns: 1fr; } }
        .card {
            background: var(--surface);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border);
            border-radius: 20px;
            padding: 24px;
            box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4);
            margin-bottom: 24px;
        }
        .card-header {
            font-size: 18px;
            font-weight: 800;
            margin-bottom: 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .form-group { margin-bottom: 18px; }
        label {
            display: block;
            font-size: 12px;
            font-weight: 700;
            color: #94A3B8;
            margin-bottom: 7px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        input, textarea, select {
            width: 100%;
            background: rgba(10, 15, 29, 0.8);
            border: 1px solid var(--border);
            color: #FFF;
            padding: 12px 14px;
            border-radius: 12px;
            font-size: 14px;
            outline: none;
            transition: all 0.2s;
        }
        input:focus, textarea:focus, select:focus {
            border-color: var(--accent);
            box-shadow: 0 0 16px rgba(0, 242, 254, 0.2);
        }
        textarea { resize: vertical; min-height: 70px; }
        .color-chips {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-top: 8px;
        }
        .color-chip {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            cursor: pointer;
            border: 2px solid transparent;
            transition: transform 0.2s, border-color 0.2s;
        }
        .color-chip:hover { transform: scale(1.15); }
        .color-chip.active { border-color: #FFF; transform: scale(1.15); box-shadow: 0 0 12px currentColor; }
        .color-row {
            display: flex;
            gap: 12px;
            align-items: center;
        }
        .color-picker-input {
            width: 44px;
            height: 44px;
            padding: 0;
            border: none;
            border-radius: 12px;
            cursor: pointer;
            background: transparent;
        }
        .btn-submit {
            width: 100%;
            padding: 14px;
            border-radius: 14px;
            border: none;
            background: linear-gradient(135deg, var(--accent), #8B5CF6);
            color: #000;
            font-size: 15px;
            font-weight: 800;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 8px 24px rgba(0, 242, 254, 0.3);
        }
        .btn-submit:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(0, 242, 254, 0.45); }
        .btn-stop {
            background: #EF4444;
            color: #FFF;
            border: none;
            padding: 8px 14px;
            border-radius: 10px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn-stop:hover { background: #DC2626; }
        /* Android Live Phone Simulator */
        .simulator-box {
            background: #000;
            border: 1px solid rgba(255, 255, 255, 0.15);
            border-radius: 20px;
            padding: 16px;
            box-shadow: 0 20px 50px rgba(0, 0, 0, 0.7);
            margin-bottom: 24px;
        }
        .phone-notch-bar {
            display: flex;
            justify-content: space-between;
            font-size: 11px;
            color: #94A3B8;
            margin-bottom: 12px;
            padding: 0 4px;
        }
        .notif-card {
            background: #1E293B;
            border-radius: 16px;
            padding: 14px;
            border: 1px solid rgba(255, 255, 255, 0.08);
            position: relative;
            overflow: hidden;
        }
        .notif-header {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 12px;
            color: #94A3B8;
            margin-bottom: 8px;
        }
        .notif-app-icon {
            width: 18px;
            height: 18px;
            border-radius: 4px;
            background: var(--accent);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #000;
            font-weight: 900;
            font-size: 10px;
        }
        .notif-badge-tag {
            font-size: 10px;
            font-weight: 800;
            padding: 2px 8px;
            border-radius: 6px;
            background: rgba(255, 255, 255, 0.08);
            color: var(--accent);
            text-transform: uppercase;
        }
        .notif-title {
            font-size: 15px;
            font-weight: 800;
            color: #FFF;
            margin-bottom: 4px;
        }
        .notif-msg {
            font-size: 13px;
            color: #CBD5E1;
            line-height: 1.4;
            margin-bottom: 10px;
        }
        .notif-banner {
            width: 100%;
            height: 150px;
            border-radius: 10px;
            object-fit: cover;
            margin-bottom: 10px;
            background: #0F172A;
            display: block;
        }
        .notif-action-btn {
            display: inline-block;
            padding: 7px 14px;
            border-radius: 8px;
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid var(--border);
            color: var(--accent);
            font-size: 12px;
            font-weight: 700;
        }
        /* Code Box */
        .code-tabs {
            display: flex;
            gap: 8px;
            margin-bottom: 12px;
        }
        .tab-btn {
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border);
            color: #94A3B8;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
        }
        .tab-btn.active {
            background: var(--accent);
            color: #000;
            border-color: var(--accent);
        }
        pre {
            background: #0A0F1D;
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 14px;
            font-size: 12px;
            color: #A5B4FC;
            overflow-x: auto;
            white-space: pre-wrap;
            position: relative;
        }
        .btn-copy {
            position: absolute;
            top: 10px;
            right: 10px;
            background: rgba(255, 255, 255, 0.1);
            border: 1px solid var(--border);
            color: #FFF;
            font-size: 11px;
            padding: 4px 8px;
            border-radius: 6px;
            cursor: pointer;
        }
        .btn-copy:hover { background: rgba(255, 255, 255, 0.2); }
        /* Table */
        table { width: 100%; border-collapse: collapse; margin-top: 12px; }
        th, td { padding: 10px 12px; text-align: left; font-size: 12px; border-bottom: 1px solid var(--border); }
        th { color: #94A3B8; font-weight: 700; }
        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            padding: 14px 22px;
            border-radius: 12px;
            font-size: 13px;
            font-weight: 700;
            color: #FFF;
            display: none;
            z-index: 999;
            box-shadow: 0 10px 30px rgba(0,0,0,0.8);
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div>
                <div class="brand-title">⚡ CYROSONIC ADMIN STUDIO</div>
                <div style="font-size: 12px; color: #64748B; margin-top: 2px;">Cloud Broadcasts • Design Engine • Live Sync Controls</div>
            </div>
            <div class="badge-secret">CONFIDENTIAL • /adminbyhunter</div>
        </header>

        <div class="metrics-bar" id="metricsBar">
            <div class="metric-pill"><span>🟢 Server Status:</span> <strong style="color:#10B981;">ONLINE</strong></div>
            <div class="metric-pill"><span>🎧 Active Listening Parties:</span> <strong id="mParties">0</strong></div>
            <div class="metric-pill"><span>📢 Broadcast History:</span> <strong id="mBroadcasts">0</strong></div>
            <div class="metric-pill"><span>⏱️ Server Uptime:</span> <strong id="mUptime">0s</strong></div>
        </div>

        <div class="grid-layout">
            <!-- Left Column: Broadcast Design Studio -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <span>📢 Dispatch New Broadcast</span>
                        <span style="font-size: 11px; color: #94A3B8;">Real-Time Mobile Push</span>
                    </div>

                    <form id="broadcastForm">
                        <div class="form-group">
                            <label>Admin Key (Set in Render Dashboard)</label>
                            <input type="password" id="adminKey" placeholder="Enter ADMIN_KEY" required>
                            <div style="margin-top:6px; display:flex; align-items:center; gap:6px;">
                                <input type="checkbox" id="rememberKey" style="width:auto;" checked>
                                <span style="font-size:11px; color:#94A3B8;">Remember key in this browser</span>
                            </div>
                        </div>

                        <div class="form-group">
                            <label>Notification Title</label>
                            <input type="text" id="title" placeholder="e.g. 🎧 Lost In The Echo • Lossless Drop" value="🎧 New Lossless Premiere" required>
                        </div>

                        <div class="form-group">
                            <label>Message Content</label>
                            <textarea id="message" placeholder="What should millions of listeners hear today?" required>Experience the freshest melodic drop in pure master-tier audio quality on CyroSonic.</textarea>
                        </div>

                        <div class="form-group">
                            <label>Badge Tag & Category</label>
                            <input type="text" id="badgeText" placeholder="e.g. 👑 OWNER DROP or 🔥 TRENDING" value="👑 OWNER EXCLUSIVE">
                        </div>

                        <div class="form-group">
                            <label>Theme Accent Color</label>
                            <div class="color-row">
                                <input type="color" id="accentColor" class="color-picker-input" value="#00F2FE">
                                <input type="text" id="accentColorHex" value="#00F2FE" style="max-width:120px;">
                                <div class="color-chips">
                                    <div class="color-chip active" style="background:#00F2FE;" onclick="pickColor('#00F2FE')"></div>
                                    <div class="color-chip" style="background:#FFD700;" onclick="pickColor('#FFD700')"></div>
                                    <div class="color-chip" style="background:#FF007F;" onclick="pickColor('#FF007F')"></div>
                                    <div class="color-chip" style="background:#8B5CF6;" onclick="pickColor('#8B5CF6')"></div>
                                    <div class="color-chip" style="background:#10B981;" onclick="pickColor('#10B981')"></div>
                                    <div class="color-chip" style="background:#EF4444;" onclick="pickColor('#EF4444')"></div>
                                </div>
                            </div>
                        </div>

                        <div class="form-group">
                            <label>Banner Artwork Image URL (Optional)</label>
                            <input type="url" id="imageUrl" placeholder="https://images.unsplash.com/... or leave blank" value="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800">
                        </div>

                        <div class="form-group">
                            <label>Target Action / Destination</label>
                            <input type="text" id="trackQuery" placeholder="Song Name, Video ID, or Party Code (e.g. Starboy or 849201)" value="Starboy The Weeknd">
                        </div>

                        <div class="form-group">
                            <label>Action Button Label</label>
                            <input type="text" id="actionText" placeholder="e.g. ▶️ Listen Now or 🚀 Join Party" value="▶️ Listen Now">
                        </div>

                        <div class="form-group">
                            <label>Broadcast Lifetime (TTL Expiration)</label>
                            <select id="ttlHours">
                                <option value="1">⚡ 1 Hour (Urgent Flash Drop)</option>
                                <option value="6">⏳ 6 Hours</option>
                                <option value="12">🌙 12 Hours</option>
                                <option value="24" selected>☀️ 24 Hours (Standard Daily Drop)</option>
                                <option value="72">📅 3 Days</option>
                                <option value="168">🗓️ 7 Days</option>
                                <option value="0">♾️ Permanent (Until manually stopped)</option>
                            </select>
                            <div style="font-size:11px; color:#64748B; margin-top:4px;">When expired, future app installs will NEVER receive this broadcast.</div>
                        </div>

                        <button type="submit" class="btn-submit" id="btnPublish">🚀 Publish Broadcast to All Devices</button>
                    </form>
                </div>
            </div>

            <!-- Right Column: Live Simulator, Script Hub & Active Controls -->
            <div>
                <!-- 📱 Android Phone Simulator -->
                <div class="simulator-box">
                    <div class="phone-notch-bar">
                        <span>9:41</span>
                        <span>📶 5G • 100% 🔋</span>
                    </div>
                    <div class="notif-card" id="previewCard">
                        <div class="notif-header">
                            <div class="notif-app-icon" id="previewIcon">CS</div>
                            <span style="font-weight:800; color:#FFF;">CyroSonic</span>
                            <span>•</span>
                            <span class="notif-badge-tag" id="previewBadge">👑 OWNER EXCLUSIVE</span>
                            <span style="margin-left:auto; font-size:11px;">Just now</span>
                        </div>
                        <div class="notif-title" id="previewTitle">🎧 New Lossless Premiere</div>
                        <div class="notif-msg" id="previewMsg">Experience the freshest melodic drop in pure master-tier audio quality on CyroSonic.</div>
                        <img src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800" id="previewImg" class="notif-banner" alt="Banner">
                        <div class="notif-action-btn" id="previewBtn">▶️ Listen Now</div>
                    </div>
                </div>

                <!-- 💻 Multi-Language Code Dispatchers -->
                <div class="card">
                    <div class="card-header">
                        <span>💻 Script Dispatchers</span>
                        <span style="font-size:11px; color:#94A3B8;">Dynamic Code Sync</span>
                    </div>
                    <div class="code-tabs">
                        <button class="tab-btn active" onclick="switchLang('py')">🐍 Python</button>
                        <button class="tab-btn" onclick="switchLang('curl')">⚡ cURL</button>
                        <button class="tab-btn" onclick="switchLang('cpp')">🛡️ C++</button>
                        <button class="tab-btn" onclick="switchLang('node')">🚀 Node.js</button>
                    </div>
                    <div style="position:relative;">
                        <button class="btn-copy" onclick="copyCode()">📋 Copy</button>
                        <pre id="codeSnippet"></pre>
                    </div>
                </div>

                <!-- ⏹️ Active Broadcast & History -->
                <div class="card">
                    <div class="card-header">
                        <span>📢 Active Broadcast Control</span>
                        <button class="btn-stop" onclick="stopActiveBroadcasts()">⏹️ Stop All Active</button>
                    </div>
                    <div id="activeBcContent" style="font-size:13px; color:#94A3B8;">Loading status...</div>

                    <div style="margin-top:20px; font-weight:800; font-size:14px;">Recent Broadcast Archive</div>
                    <div style="max-height:220px; overflow-y:auto;">
                        <table id="historyTable">
                            <thead>
                                <tr>
                                    <th>Title</th>
                                    <th>Status</th>
                                    <th>Expires</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody id="historyBody"></tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div id="toast" class="toast"></div>

    <script>
        let currentLang = 'py';
        const rememberedKey = localStorage.getItem('cyrosonic_admin_key');
        if (rememberedKey) {
            document.getElementById('adminKey').value = rememberedKey;
        }

        function pickColor(hex) {
            document.getElementById('accentColor').value = hex;
            document.getElementById('accentColorHex').value = hex;
            document.querySelectorAll('.color-chip').forEach(c => {
                c.classList.toggle('active', c.style.backgroundColor === hex || rgbToHex(c.style.backgroundColor) === hex.toLowerCase());
            });
            updatePreview();
        }

        function rgbToHex(rgb) {
            const result = rgb.match(/\\d+/g);
            if (!result) return '';
            return '#' + ((1 << 24) + (parseInt(result[0]) << 16) + (parseInt(result[1]) << 8) + parseInt(result[2])).toString(16).slice(1);
        }

        document.getElementById('accentColor').addEventListener('input', (e) => {
            document.getElementById('accentColorHex').value = e.target.value;
            updatePreview();
        });
        document.getElementById('accentColorHex').addEventListener('input', (e) => {
            if (/^#[0-9A-Fa-f]{6}$/.test(e.target.value)) {
                document.getElementById('accentColor').value = e.target.value;
                updatePreview();
            }
        });

        const liveInputs = ['title', 'message', 'badgeText', 'imageUrl', 'actionText', 'trackQuery', 'adminKey', 'ttlHours'];
        liveInputs.forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('input', updatePreview);
        });

        function updatePreview() {
            const title = document.getElementById('title').value || 'Notification Title';
            const msg = document.getElementById('message').value || 'Message body...';
            const badge = document.getElementById('badgeText').value || 'CYROSONIC';
            const color = document.getElementById('accentColor').value || '#00F2FE';
            const img = document.getElementById('imageUrl').value;
            const btn = document.getElementById('actionText').value || '▶️ Listen Now';

            document.documentElement.style.setProperty('--accent', color);
            document.getElementById('previewTitle').textContent = title;
            document.getElementById('previewMsg').textContent = msg;
            document.getElementById('previewBadge').textContent = badge;
            document.getElementById('previewBadge').style.color = color;
            document.getElementById('previewIcon').style.background = color;
            document.getElementById('previewBtn').textContent = btn;
            document.getElementById('previewBtn').style.color = color;

            const imgEl = document.getElementById('previewImg');
            if (img && img.startsWith('http')) {
                imgEl.src = img;
                imgEl.style.display = 'block';
            } else {
                imgEl.style.display = 'none';
            }

            renderSnippet();
        }

        function renderSnippet() {
            const key = document.getElementById('adminKey').value || 'YOUR_RENDER_ADMIN_KEY';
            const title = document.getElementById('title').value || 'Announcement';
            const msg = document.getElementById('message').value || 'Message';
            const color = document.getElementById('accentColor').value || '#00F2FE';
            const badge = document.getElementById('badgeText').value || 'CYROSONIC';
            const track = document.getElementById('trackQuery').value || 'Starboy';
            const img = document.getElementById('imageUrl').value || '';
            const action = document.getElementById('actionText').value || '▶️ Listen Now';
            const ttl = document.getElementById('ttlHours').value || '24';

            const payload = {
                adminKey: key,
                title: title,
                message: msg,
                trackQuery: track,
                imageUrl: img,
                actionText: action,
                accentColor: color,
                badgeText: badge,
                ttlHours: Number(ttl)
            };

            const jsonStr = JSON.stringify(payload, null, 2);

            let code = '';
            if (currentLang === 'py') {
                code = \`import requests

payload = \${JSON.stringify(payload, null, 4)}

res = requests.post('https://cyrosonic.com/api/broadcast', json=payload)
print("Status:", res.status_code)
print(res.json())\`;
            } else if (currentLang === 'curl') {
                code = \`curl -X POST https://cyrosonic.com/api/broadcast \\\\
  -H "Content-Type: application/json" \\\\
  -d '\${JSON.stringify(payload)}'\`;
            } else if (currentLang === 'cpp') {
                const escaped = JSON.stringify(JSON.stringify(payload));
                code = \`#include <iostream>
#include <curl/curl.h>

int main() {
    CURL* curl = curl_easy_init();
    if(curl) {
        struct curl_slist* headers = NULL;
        headers = curl_slist_append(headers, "Content-Type: application/json");
        curl_easy_setopt(curl, CURLOPT_URL, "https://cyrosonic.com/api/broadcast");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, \${escaped});
        CURLcode res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
    }
    return 0;
}\`;
            } else if (currentLang === 'node') {
                code = \`const res = await fetch('https://cyrosonic.com/api/broadcast', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(\${jsonStr})
});
const data = await res.json();
console.log(data);\`;
            }

            document.getElementById('codeSnippet').textContent = code;
        }

        function switchLang(lang) {
            currentLang = lang;
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            event.target.classList.add('active');
            renderSnippet();
        }

        function copyCode() {
            navigator.clipboard.writeText(document.getElementById('codeSnippet').textContent);
            showToast('📋 Code copied to clipboard!', '#10B981');
        }

        function showToast(msg, bg) {
            const t = document.getElementById('toast');
            t.textContent = msg;
            t.style.background = bg;
            t.style.display = 'block';
            setTimeout(() => { t.style.display = 'none'; }, 4000);
        }

        // Form Submit
        document.getElementById('broadcastForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const key = document.getElementById('adminKey').value;
            if (document.getElementById('rememberKey').checked) {
                localStorage.setItem('cyrosonic_admin_key', key);
            } else {
                localStorage.removeItem('cyrosonic_admin_key');
            }

            const payload = {
                adminKey: key,
                title: document.getElementById('title').value,
                message: document.getElementById('message').value,
                trackQuery: document.getElementById('trackQuery').value,
                imageUrl: document.getElementById('imageUrl').value,
                actionText: document.getElementById('actionText').value,
                accentColor: document.getElementById('accentColor').value,
                badgeText: document.getElementById('badgeText').value,
                ttlHours: document.getElementById('ttlHours').value
            };

            const btn = document.getElementById('btnPublish');
            btn.disabled = true;
            btn.textContent = '⏳ Dispatching Broadcast...';

            try {
                const res = await fetch('/api/broadcast', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                if (data.success) {
                    showToast('🚀 Broadcast published successfully to all devices!', '#10B981');
                    loadData();
                } else {
                    showToast('❌ Error: ' + (data.error || 'Failed to publish'), '#EF4444');
                }
            } catch (err) {
                showToast('❌ Network error: ' + err.message, '#EF4444');
            } finally {
                btn.disabled = false;
                btn.textContent = '🚀 Publish Broadcast to All Devices';
            }
        });

        async function stopActiveBroadcasts() {
            const key = document.getElementById('adminKey').value;
            if (!key) return showToast('Please enter your Admin Key first', '#EF4444');
            if (!confirm('Are you sure you want to stop all active broadcasts? New devices will no longer receive them.')) return;

            try {
                const res = await fetch('/api/broadcast/stop', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ adminKey: key })
                });
                const data = await res.json();
                if (data.success) {
                    showToast(data.message, '#10B981');
                    loadData();
                } else {
                    showToast('Error: ' + data.error, '#EF4444');
                }
            } catch (e) {
                showToast('Network error: ' + e.message, '#EF4444');
            }
        }

        async function deleteBroadcast(id) {
            const key = document.getElementById('adminKey').value;
            if (!key) return showToast('Please enter your Admin Key first', '#EF4444');
            if (!confirm('Delete this broadcast from history?')) return;

            try {
                const res = await fetch('/api/broadcast/' + id, {
                    method: 'DELETE',
                    headers: { 'x-admin-key': key }
                });
                const data = await res.json();
                if (data.success) {
                    showToast('Deleted broadcast', '#10B981');
                    loadData();
                } else {
                    showToast('Error: ' + data.error, '#EF4444');
                }
            } catch (e) {
                showToast('Network error: ' + e.message, '#EF4444');
            }
        }

        async function loadData() {
            try {
                // Fetch metrics
                const mRes = await fetch('/api/admin/metrics');
                const mData = await mRes.json();
                document.getElementById('mParties').textContent = mData.activePartyRooms || 0;
                document.getElementById('mBroadcasts').textContent = mData.totalBroadcasts || 0;
                document.getElementById('mUptime').textContent = Math.floor(mData.uptimeSeconds / 60) + ' min';

                // Fetch broadcasts
                const bRes = await fetch('/api/broadcasts');
                const bData = await bRes.json();
                const list = bData.broadcasts || [];

                const active = list.find(b => b.isActive);
                const activeBox = document.getElementById('activeBcContent');
                if (active) {
                    const timeLeft = active.expiresAt ? Math.max(0, Math.round((active.expiresAt - Date.now()) / 3600000)) + 'h remaining' : 'Permanent';
                    activeBox.innerHTML = \`
                        <div style="background:rgba(16,185,129,0.1); border:1px solid #10B981; border-radius:12px; padding:12px;">
                            <div style="display:flex; justify-content:space-between; align-items:center;">
                                <strong style="color:#FFF;">\${active.title}</strong>
                                <span style="font-size:11px; color:#10B981; font-weight:800;">🟢 LIVE (\${timeLeft})</span>
                            </div>
                            <div style="font-size:12px; color:#CBD5E1; margin-top:4px;">\${active.message}</div>
                        </div>
                    \`;
                } else {
                    activeBox.innerHTML = '<span style="color:#64748B;">No broadcast is currently active. Devices will receive normal recommendations.</span>';
                }

                // Table
                const tbody = document.getElementById('historyBody');
                tbody.innerHTML = '';
                list.slice(0, 15).forEach(b => {
                    const tr = document.createElement('tr');
                    const statusHtml = b.isActive
                        ? '<span style="color:#10B981; font-weight:700;">Active</span>'
                        : (b.isExpired ? '<span style="color:#64748B;">Expired</span>' : '<span style="color:#EF4444;">Stopped</span>');
                    const expiresText = b.expiresAt ? new Date(b.expiresAt).toLocaleTimeString() : 'None';
                    tr.innerHTML = \`
                        <td><strong style="color:#FFF;">\${b.title}</strong></td>
                        <td>\${statusHtml}</td>
                        <td>\${expiresText}</td>
                        <td>
                            <button onclick="deleteBroadcast('\${b.id}')" style="background:transparent; border:none; color:#EF4444; cursor:pointer; font-size:14px;">🗑️</button>
                        </td>
                    \`;
                    tbody.appendChild(tr);
                });

            } catch (_) {}
        }

        updatePreview();
        loadData();
        setInterval(loadData, 10000);
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
        }
        .btn-app {
            background: linear-gradient(135deg, #06B6D4, #8B5CF6);
            color: #FFFFFF;
        }
        .btn-apk {
            background: rgba(255, 255, 255, 0.08);
            color: #CBD5E1;
            border: 1px solid rgba(255, 255, 255, 0.12);
        }
    </style>
</head>
<body>
    <div class="card">
        <img src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500" class="cover" alt="Album Art">
        <h1>Open in CyroSonic</h1>
        <p>Listen with lossless sound, synchronized lyrics & taste radio.</p>
        <a href="cyrosonic://track/${trackId}" class="btn btn-app">⚡ Open in CyroSonic App</a>
        <a href="/download/CyroSonic-Release.apk" class="btn btn-apk">⬇️ Download Free APK</a>
    </div>
</body>
</html>`);
});

let yt = null;

// --- YouTube Search Endpoint ---
app.get('/youtube/search', async (req, res) => {
    try {
        const q = (req.query.q || '').trim();
        if (!q) return res.json({ success: true, results: [] });
        if (!yt) yt = await Innertube.create({ cache: new UniversalCache(false) });
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
    } catch (error) {
        console.error("YouTube Search Error:", error.message);
        res.json({ success: false, results: [], error: error.message });
    }
});

// --- YouTube Audio Stream Decryption Endpoint ---
app.get('/youtube/stream', async (req, res) => {
    try {
        const videoId = (req.query.id || '').trim();
        if (!videoId) return res.status(400).json({ error: "Missing video ID" });
        if (!yt) yt = await Innertube.create({ cache: new UniversalCache(false) });
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
    } catch (error) {
        console.error("YouTube Stream Error:", error.message);
        res.json({ success: false, error: error.message });
    }
});

// ==========================================
// 🎧 CYROSONIC LISTENING PARTY (SYNC ROOMS)
// ==========================================
const partyRooms = new Map(); // roomCode -> { code, hostId, track, positionMs, isPlaying, updatedAt, listeners: number }

function cleanExpiredRooms() {
    const now = Date.now();
    for (const [code, room] of partyRooms.entries()) {
        if (now - room.updatedAt > 4 * 60 * 60 * 1000) { // 4 hours
            partyRooms.delete(code);
        }
    }
}

app.post('/api/party/create', (req, res) => {
    cleanExpiredRooms();
    const { hostId, track, isPlaying = true, positionMs = 0 } = req.body;
    let roomCode = '';
    for (let i = 0; i < 6; i++) {
        roomCode += Math.floor(Math.random() * 10);
    }
    while (partyRooms.has(roomCode)) {
        roomCode = '';
        for (let i = 0; i < 6; i++) roomCode += Math.floor(Math.random() * 10);
    }
    const party = {
        code: roomCode,
        hostId: hostId || 'host_' + Date.now(),
        track: track || null,
        positionMs: Number(positionMs) || 0,
        isPlaying: Boolean(isPlaying),
        updatedAt: Date.now(),
        listeners: 1
    };
    partyRooms.set(roomCode, party);
    res.json({ success: true, roomCode, party });
});

app.post('/api/party/join', (req, res) => {
    cleanExpiredRooms();
    const { roomCode } = req.body;
    if (!roomCode || !partyRooms.has(roomCode)) {
        return res.status(404).json({ success: false, error: "Listening Party room not found or expired" });
    }
    const party = partyRooms.get(roomCode);
    party.listeners = (party.listeners || 1) + 1;
    res.json({ success: true, roomCode, party });
});

app.post('/api/party/sync', (req, res) => {
    const { roomCode, hostId, track, isPlaying, positionMs } = req.body;
    if (!roomCode || !partyRooms.has(roomCode)) {
        return res.status(404).json({ success: false, error: "Room not found" });
    }
    const party = partyRooms.get(roomCode);
    // If host is broadcasting changes, update room state
    if (hostId && party.hostId === hostId) {
        if (track) party.track = track;
        if (typeof isPlaying === 'boolean') party.isPlaying = isPlaying;
        if (typeof positionMs === 'number') party.positionMs = positionMs;
        party.updatedAt = Date.now();
    }
    res.json({ success: true, party });
});

app.post('/api/party/leave', (req, res) => {
    const { roomCode, hostId } = req.body;
    if (roomCode && partyRooms.has(roomCode)) {
        const party = partyRooms.get(roomCode);
        if (hostId && party.hostId === hostId) {
            partyRooms.delete(roomCode);
        } else {
            party.listeners = Math.max(1, (party.listeners || 2) - 1);
        }
    }
    res.json({ success: true });
});

app.get('/party/:code', (req, res) => {
    const code = req.params.code;
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Join Listening Party ${code} - CyroSonic</title>
    <style>
        body { margin:0; background:#090D16; color:#FFF; font-family:-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display:flex; align-items:center; justify-content:center; min-height:100vh; padding:20px; box-sizing:border-box; }
        .card { background:rgba(255,255,255,0.06); backdrop-filter:blur(20px); border:1px solid rgba(255,255,255,0.12); border-radius:24px; padding:36px; max-width:400px; width:100%; text-align:center; box-shadow:0 20px 50px rgba(0,0,0,0.8); }
        .code-box { font-size:36px; font-weight:900; letter-spacing:6px; color:#00F2FE; margin:20px 0; padding:14px; background:rgba(0,242,254,0.1); border-radius:16px; border:1px dashed #00F2FE; }
        .btn { display:block; padding:14px; margin-top:16px; border-radius:14px; font-weight:800; text-decoration:none; background:linear-gradient(135deg, #00F2FE, #8B5CF6); color:#FFF; }
    </style>
</head>
<body>
    <div class="card">
        <h1>🎧 Listening Party</h1>
        <p>You've been invited to listen together on CyroSonic in real-time sync!</p>
        <div class="code-box">${code}</div>
        <a href="cyrosonic://party/${code}" class="btn">🚀 Join in CyroSonic App</a>
        <a href="/download/CyroSonic-Release.apk" style="display:block;margin-top:14px;color:#94A3B8;font-size:13px;text-decoration:none;">Don't have CyroSonic? Download Free APK</a>
    </div>
</body>
</html>`);
});

app.listen(port, '0.0.0.0', () => {
    const publicUrl = process.env.RENDER ? 'https://cyrosonic.com' : `http://localhost:${port}`;
    console.log(`CyroSonic Production API & 3D Web Server active at ${publicUrl} (internal container port: ${port})`);
});


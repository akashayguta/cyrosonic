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
            enableOtaEqualizer: true,
            bassBoostDb: 1.5
        }
    });
});

// --- Server-Driven Broadcast API ---
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

// Legacy /admin route disabled as requested — nobody can find it!
app.get('/admin', (req, res) => {
    res.status(404).send("Page not found");
});

// --- Secret Admin System (/adminbyhunter) & Multi-Language Dispatcher ---
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
            margin-bottom: 24px;
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
            transition: transform 0.15s;
        }
        .btn-broadcast:hover { transform: translateY(-2px); }
        /* Code Generator Tabs */
        .code-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
        .code-tab-btn {
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid rgba(255, 255, 255, 0.1);
            color: #94A3B8;
            padding: 8px 14px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 12px;
            font-weight: 700;
        }
        .code-tab-btn.active {
            background: rgba(0, 242, 254, 0.2);
            color: #00F2FE;
            border-color: rgba(0, 242, 254, 0.4);
        }
        pre {
            background: #05070D;
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 12px;
            padding: 14px;
            font-size: 12px;
            color: #A5F3FC;
            overflow-x: auto;
            white-space: pre-wrap;
        }
        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            padding: 14px 22px;
            border-radius: 12px;
            font-size: 14px;
            font-weight: 700;
            display: none;
            z-index: 100;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo">⚡ CYROSONIC ADMIN</div>
            <div class="tag">SECRET PORTAL • /adminbyhunter</div>
        </header>

        <div class="grid">
            <div class="card">
                <h2>📢 Dispatch Live Cloud Broadcast</h2>
                <form id="broadcastForm">
                    <div class="form-group">
                        <label>Admin Password (Configured in Render ADMIN_KEY)</label>
                        <input type="password" id="adminKey" placeholder="Enter ADMIN_KEY" required>
                    </div>
                    <div class="form-group">
                        <label>Notification Title</label>
                        <input type="text" id="title" placeholder="e.g. 🎧 Cosmic Chill Session is Live" required>
                    </div>
                    <div class="form-group">
                        <label>Message Content</label>
                        <textarea id="message" placeholder="e.g. Listen to the freshest melodic techno release now streaming lossless." required></textarea>
                    </div>
                    <div class="form-group">
                        <label>Track Search Query or Video ID (Optional)</label>
                        <input type="text" id="trackQuery" placeholder="e.g. Blinding Lights The Weeknd or 4NR4vK2ZlA">
                    </div>
                    <div class="form-group">
                        <label>Banner Image URL (Optional)</label>
                        <input type="url" id="imageUrl" placeholder="https://images.unsplash.com/...">
                    </div>
                    <button type="submit" class="btn-broadcast">🚀 Publish Broadcast Instantly</button>
                </form>
            </div>

            <div>
                <div class="card">
                    <h2>💻 Script Dispatchers</h2>
                    <p style="font-size:12px; color:#94A3B8; margin-bottom:12px;">Trigger broadcasts directly from code:</p>
                    <div class="code-tabs">
                        <button class="code-tab-btn active" onclick="showTab('py')">🐍 Python</button>
                        <button class="code-tab-btn" onclick="showTab('curl')">⚡ cURL</button>
                        <button class="code-tab-btn" onclick="showTab('cpp')">🛡️ C++</button>
                        <button class="code-tab-btn" onclick="showTab('node')">🚀 Node.js</button>
                    </div>
                    <pre id="codeBox"></pre>
                </div>
            </div>
        </div>
    </div>

    <div id="toast" class="toast"></div>

    <script>
        const snippets = {
            py: \`import requests

res = requests.post('https://cyrosonic.com/api/broadcast', json={
    'adminKey': 'YOUR_RENDER_ADMIN_KEY',
    'title': '⚡ Live Announcement',
    'message': 'Fresh music discovery is waiting for you.',
    'trackQuery': 'Starboy The Weeknd'
})
print(res.json())\`,
            curl: \`curl -X POST https://cyrosonic.com/api/broadcast \\\\
  -H "Content-Type: application/json" \\\\
  -d '{"adminKey":"YOUR_RENDER_ADMIN_KEY","title":"⚡ Live Announcement","message":"Fresh tracks live now."}'\`,
            cpp: \`#include <iostream>
#include <curl/curl.h>

int main() {
    CURL* curl = curl_easy_init();
    if(curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "https://cyrosonic.com/api/broadcast");
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, "{\\"adminKey\\":\\"YOUR_RENDER_ADMIN_KEY\\",\\"title\\":\\"⚡ C++ Broadcast\\",\\"message\\":\\"Automated push.\\"}");
        curl_easy_perform(curl);
        curl_easy_cleanup(curl);
    }
    return 0;
}\`,
            node: \`const res = await fetch('https://cyrosonic.com/api/broadcast', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    adminKey: 'YOUR_RENDER_ADMIN_KEY',
    title: '⚡ Live Drop',
    message: 'New release streaming on CyroSonic.'
  })
});
console.log(await res.json());\`
        };

        function showTab(lang) {
            document.querySelectorAll('.code-tab-btn').forEach(b => b.classList.remove('active'));
            event.target.classList.add('active');
            document.getElementById('codeBox').textContent = snippets[lang];
        }
        showTab('py');

        document.getElementById('broadcastForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const toast = document.getElementById('toast');
            try {
                const res = await fetch('/api/broadcast', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        adminKey: document.getElementById('adminKey').value,
                        title: document.getElementById('title').value,
                        message: document.getElementById('message').value,
                        trackQuery: document.getElementById('trackQuery').value,
                        imageUrl: document.getElementById('imageUrl').value
                    })
                });
                const data = await res.json();
                if (data.success) {
                    toast.textContent = '🚀 Broadcast successfully published!';
                    toast.style.background = '#10B981';
                } else {
                    toast.textContent = '❌ Error: ' + (data.error || 'Failed');
                    toast.style.background = '#EF4444';
                }
                toast.style.display = 'block';
                setTimeout(() => toast.style.display = 'none', 4000);
            } catch (err) {
                toast.textContent = '❌ Network Error: ' + err.message;
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


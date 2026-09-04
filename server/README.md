# CyroSonic API & AI Backend

Production Node.js & Express backend for **CyroSonic** (formerly HunterXMusic).
Serves high-performance AI endpoints, YouTube InnerTube streaming resolves, and cross-device sync.

## Domain
* Production URL: `https://api.cyrosonic.com`
* Main Portal: `https://cyrosonic.com`

## Endpoints
* `GET /` — Health check
* `GET /ai/gpt-5?text=...` — Music AI prompt resolver
* `GET /api/stream/:videoId` — Stream format resolver

## Deployment
Compatible with Render, Koyeb, Railway, or any Node.js 18+ host.
Command: `npm install && npm start`

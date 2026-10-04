# TrustDesk frontend

React + Vite dashboard for the TrustDesk support workflow. It calls the backend API as documented in `../docs/API.md`.

## Run locally

1. Start the backend on `http://localhost:8080` and configure `TRUSTDESK_DEMO_TOKEN` (and optionally `TRUSTDESK_MANAGER_TOKEN`) as described in the repository README.
2. In this directory, install and start the frontend:

   ```powershell
   npm install
   npm run dev
   ```

3. Open the Vite URL shown in the terminal. Enter the demo token in the connection screen. A manager token is optional until an approval is needed.

By default, `/api` requests are proxied to `http://localhost:8080`. Copy `.env.example` to `.env.local` to override `VITE_API_BASE_URL` or `VITE_API_PROXY_TARGET`. A direct cross-origin API URL can require backend CORS configuration; the local Vite proxy avoids that requirement for development.

## Demo walkthrough

Follow [`../docs/DEMO.md`](../docs/DEMO.md) for the end-to-end frontend tour: ticket triage, grounded drafts and
citations, guardrails, manager approval, safe execution/idempotency, traces, adversarial cases, and evaluation.

Tokens are stored in this browser's local storage and can be removed with **Disconnect tokens**.

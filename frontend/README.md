# SmartAPS Frontend

This is the React + TypeScript dashboard for SmartAPS. It provides the management UI for jobs, resumes, candidates, interviews, analytics, and audit logs.

## What the frontend includes

- Authentication and token refresh flow
- Job, candidate, application, interview, and audit management pages
- Resume upload and candidate search flows
- Analytics dashboards and live SSE stream views
- Role-based access and unified API request handling

## Local development

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

The Vite development server runs on `http://localhost:3000` by default and proxies `/api/v1` calls to the Spring Boot app on `http://localhost:8080`.

## Environment variables

Create a `.env` file based on the template below:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_TITLE=SmartAPS
```

## Useful commands

```bash
npm run build
npm run lint
npm run dev -- --host 0.0.0.0
```

## API response contract

REST endpoints use the backend response envelope:

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 0
}
```

The shared Axios client treats code `200` as success, displays consistent messages for failed requests, and shares a single token-refresh request across concurrent `401` responses. Server-sent events use their dedicated `EventSource` flow rather than the JSON response envelope.

## Troubleshooting

- If the Vite server reports a port conflict, either stop the process using port 3000 or update the port in `vite.config.ts`.
- If API calls fail with 401s, log out and log back in so the refresh token flow can run again.
- If requests hang, confirm the backend is listening on `http://localhost:8080` and that the proxy config is still pointing to the same target.

## Notes

- Keep the backend running before testing the dashboard flows.
- Do not commit your local `.env` file.
- For production builds, configure your API host explicitly in `VITE_API_BASE_URL`.

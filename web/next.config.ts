import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

// Same-origin backend proxy (WEB_ARCHITECTURE.md § 3): rewriting /api/* to the Ktor backend
// makes its auth cookies first-party to the browser, which is what allows SameSite=Lax
// instead of SameSite=None. API_BASE_URL defaults to the local backend per
// execution/INTEGRATION_CONTRACT.md § 1 / DEPLOYMENT.md § 4.
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${API_BASE_URL}/api/:path*`,
      },
    ];
  },
};

export default withNextIntl(nextConfig);

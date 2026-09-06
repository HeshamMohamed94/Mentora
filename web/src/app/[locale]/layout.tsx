import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { routing, type AppLocale } from "@/i18n/routing";
import { QueryProvider } from "@/components/providers/query-provider";
import { themeInitScript } from "@/lib/theme/theme-script";
import "../globals.css";

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "common" });
  return {
    title: { default: t("appName"), template: `%s · ${t("appName")}` },
    description: t("tagline"),
  };
}

// Every left/right-mirroring decision is direction-driven, not locale-driven
// (design-system/LOCALIZATION.md § 1) — dir is the one thing this layout branches on locale
// for, since it's the single source of truth every logical CSS property downstream reacts to.
const RTL_LOCALES: ReadonlySet<string> = new Set(["ar"]);

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!routing.locales.includes(locale as AppLocale)) {
    notFound();
  }
  setRequestLocale(locale);

  const messages = await getMessages();
  const dir = RTL_LOCALES.has(locale) ? "rtl" : "ltr";
  const t = await getTranslations({ locale, namespace: "common" });

  return (
    <html lang={locale} dir={dir} suppressHydrationWarning>
      <head>
        {/* Runs before paint — no flash of the wrong theme (architecture/WEB_ARCHITECTURE.md § 4) */}
        <script dangerouslySetInnerHTML={{ __html: themeInitScript() }} />
      </head>
      <body>
        <a href="#main-content" className="mtx-skip-link">
          {t("skipToContent")}
        </a>
        <NextIntlClientProvider messages={messages}>
          <QueryProvider>{children}</QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
